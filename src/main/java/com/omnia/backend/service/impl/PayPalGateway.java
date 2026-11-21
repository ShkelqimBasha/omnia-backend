package com.omnia.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.math.BigDecimal;

/**
 * PayPal for order checkouts. Sandbox unless Live is chosen explicitly: Live needs both
 * {@code omnia.paypal.environment=live} and a credentials file that declares
 * {@code "environment":"live"}. Any other combination (an unknown setting, Live setting with Sandbox
 * credentials, Sandbox setting with Live credentials) leaves the gateway not ready, so it sends nothing.
 * Every checkout records the environment it was created in; callers send a checkout only to the
 * environment it belongs to (see {@link #serves}).
 */
@Component
public class PayPalGateway {
    public static final String SANDBOX = "sandbox";
    public static final String LIVE = "live";
    private static final String SANDBOX_API = "https://api-m.sandbox.paypal.com";
    private static final String LIVE_API = "https://api-m.paypal.com";
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    @Value("${omnia.paypal.enabled:false}") private boolean enabled;
    @Value("${omnia.paypal.environment:sandbox}") private String environment = SANDBOX;
    @Value("${omnia.paypal.credentials-file:}") private String file;
    @Value("${omnia.paypal.public-base:https://api.myomniaapp.com}") private String publicBase;
    private String cachedToken,cachedClient;
    private long tokenExpires;
    public PayPalGateway(ObjectMapper json) { this.json = json; }
    private JsonNode credentials() {
        try { return json.readTree(Files.readString(Path.of(file))); }
        catch (Exception ex) { throw new IllegalStateException("PayPal is not configured"); }
    }
    /** The configured setting when it is exactly "sandbox" or "live"; null for anything else. */
    String setting() { return SANDBOX.equals(environment) || LIVE.equals(environment) ? environment : null; }
    public boolean ready() {
        String setting=setting();
        if (!enabled || setting == null || file == null || file.isBlank()) return false;
        try { JsonNode c=credentials();
            // Live credentials must say so; Sandbox credentials may predate the field but never say Live.
            String declared=c.path("environment").asText("");
            boolean credentialsMatch=LIVE.equals(setting) ? LIVE.equals(declared) : declared.isEmpty() || SANDBOX.equals(declared);
            return credentialsMatch && !c.path("clientId").asText().isBlank()
                && !c.path("clientSecret").asText().isBlank() && c.path("merchantId").asText().matches("[A-Z0-9]{5,32}")
                && !c.path("webhookId").asText().isBlank() && publicBase.matches("https://[A-Za-z0-9.-]+(?::[0-9]+)?"); }
        catch (Exception ex) { return false; }
    }
    public void requireReady() { if (!ready()) throw new IllegalStateException("PayPal is not configured"); }
    /** Environment this server sends requests to ("sandbox" or "live"), or null when PayPal is not ready. */
    public String environment() { return ready() ? setting() : null; }
    /** A checkout created in {@code checkoutEnvironment} may be sent to the PayPal this server uses. */
    public static boolean serves(String served, String checkoutEnvironment) {
        return served != null && served.equals(checkoutEnvironment);
    }
    private String api() { return LIVE.equals(setting()) ? LIVE_API : SANDBOX_API; }
    public String merchant() { requireReady(); return credentials().path("merchantId").asText(); }
    protected synchronized String token() {
        requireReady(); JsonNode c=credentials();
        String client=api()+"|"+c.path("clientId").asText();
        if(client.equals(cachedClient) && cachedToken!=null && System.currentTimeMillis()<tokenExpires)return cachedToken;
        String basic=Base64.getEncoder().encodeToString((c.path("clientId").asText()+":"+c.path("clientSecret").asText()).getBytes(StandardCharsets.UTF_8));
        JsonNode result=exchange(HttpRequest.newBuilder(URI.create(api()+"/v1/oauth2/token"))
                .header("Authorization","Basic "+basic).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")));
        String token=result.path("access_token").asText();
        if(token.isBlank()) throw new IllegalStateException("PayPal authentication failed");
        cachedToken=token;cachedClient=client;
        tokenExpires=System.currentTimeMillis()+Math.max(0,Math.min(3600,result.path("expires_in").asLong(300))-60)*1000;
        return token;
    }
    public JsonNode create(String id, BigDecimal total) {
        String root=publicBase+"/api/paypal/return?checkout="+id;
        Map<String,Object> body=Map.of("intent","CAPTURE","purchase_units",List.of(Map.of(
                "reference_id",id,"custom_id",id,"payee",Map.of("merchant_id",merchant()),
                "amount",Map.of("currency_code","EUR","value",total.toPlainString()))),
                "payment_source",Map.of("paypal",Map.of("experience_context",Map.of(
                        "brand_name",LIVE.equals(setting())?"Omnia":"Omnia Sandbox","shipping_preference","NO_SHIPPING","user_action","PAY_NOW",
                        "return_url",root,"cancel_url",root+"&cancelled=true"))));
        return request("POST","/v2/checkout/orders",body,"create-"+id);
    }
    public JsonNode get(String id) { validId(id); return request("GET","/v2/checkout/orders/"+id,null,null); }
    public JsonNode capture(String id, String key) { validId(id); return request("POST","/v2/checkout/orders/"+id+"/capture",Map.of(),"capture-"+key); }
    public boolean verifyWebhook(Map<String,String> headers, JsonNode event) {
        requireReady();
        Map<String,Object> body=new HashMap<>();
        for (String name:List.of("auth-algo","cert-url","transmission-id","transmission-sig","transmission-time")) {
            String value=headers.get("paypal-"+name);
            if(value==null || value.isBlank() || value.length()>4096) return false;
            body.put(name.replace('-','_'),value);
        }
        body.put("webhook_id",credentials().path("webhookId").asText());body.put("webhook_event",event);
        return "SUCCESS".equals(request("POST","/v1/notifications/verify-webhook-signature",body,null).path("verification_status").asText());
    }
    /** The buyer's approval link, only on the approval host of the checkout's own environment. */
    public static String approval(JsonNode node, String checkoutEnvironment) {
        String host=LIVE.equals(checkoutEnvironment)?"www.paypal.com":SANDBOX.equals(checkoutEnvironment)?"www.sandbox.paypal.com":null;
        for(JsonNode link:node.path("links")) if(host!=null && Set.of("approve","payer-action").contains(link.path("rel").asText())) {
            String url=link.path("href").asText();
            try { URI u=URI.create(url); if("https".equals(u.getScheme()) && host.equals(u.getHost())
                    && u.getUserInfo()==null && u.getPort()==-1) return url; } catch(Exception ignored) {}
        }
        throw new IllegalStateException("PayPal approval link unavailable");
    }
    private JsonNode request(String method,String path,Object body,String key) {
        try {
            String bearer=token();   // checks readiness (setting and credentials agree) before any request
            HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(api()+path)).header("Authorization","Bearer "+bearer)
                    .header("Content-Type","application/json").header("Prefer","return=representation");
            if(key!=null)b.header("PayPal-Request-Id",key);
            if("GET".equals(method))b.GET();else b.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            return exchange(b);
        } catch (IllegalStateException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("PayPal unavailable; retry this payment"); }
    }
    public static final class Rejected extends IllegalStateException {
        public final String issue;
        public final String debugId;
        public Rejected(String issue,String debugId) {
            super("PayPal rejected capture: "+safe(issue)+"; debug_id="+safe(debugId));
            this.issue=safe(issue);this.debugId=safe(debugId);
        }
        static String safe(String value) {
            return value!=null && value.matches("[A-Za-z0-9_]{1,80}")?value:"UNKNOWN";
        }
        public boolean stopCapture() {
            return Set.of("COMPLIANCE_VIOLATION","MAX_NUMBER_OF_PAYMENT_ATTEMPTS_EXCEEDED").contains(issue);
        }
    }
    /**
     * PayPal could not provide evidence. NOT_FOUND and ACCESS_DENIED are never proof that a
     * payment did not happen (rotated credentials, another merchant account); callers must treat
     * every kind as "unknown". The message is the generic one used before.
     */
    public static final class Unavailable extends IllegalStateException {
        public final String kind;
        public final int status;
        public final String debugId;
        public Unavailable(String kind,int status,String debugId) {
            super("PayPal unavailable; check payment status");
            this.kind=kind;this.status=status;this.debugId=Rejected.safe(debugId);
        }
    }
    private JsonNode exchange(HttpRequest.Builder b) {
        try {
            HttpResponse<String> r=http.send(b.timeout(Duration.ofSeconds(18)).header("Accept","application/json").build(),HttpResponse.BodyHandlers.ofString());
            if(r.body().length()>262144)throw new IllegalStateException("PayPal response too large");
            int code=r.statusCode();
            if(code==404 || code==401 || code==403) {
                String debug=null;
                try { debug=json.readTree(r.body()).path("debug_id").asText(null); } catch(Exception ignored) {}
                throw new Unavailable(code==404?"NOT_FOUND":"ACCESS_DENIED",code,debug);
            }
            JsonNode response=json.readTree(r.body());
            if(r.statusCode()<200 || r.statusCode()>=300) {
                if(r.statusCode()==422) {
                    for(JsonNode detail:response.path("details")) {
                        String issue=detail.path("issue").asText();
                        if(Set.of("COMPLIANCE_VIOLATION","MAX_NUMBER_OF_PAYMENT_ATTEMPTS_EXCEEDED").contains(issue))
                            throw new Rejected(issue,response.path("debug_id").asText());
                    }
                }
                throw new IllegalStateException("PayPal unavailable; check payment status");
            }
            return response;
        } catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new IllegalStateException("PayPal request interrupted"); }
        catch(IllegalStateException ex) { throw ex; }
        catch(Exception ex) { throw new IllegalStateException("PayPal unavailable; check payment status"); }
    }
    private static void validId(String id) { if(id==null || !id.matches("[A-Z0-9]{5,64}")) throw new IllegalArgumentException("Invalid PayPal ID"); }
}
