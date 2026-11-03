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

/** Sandbox only: no runtime setting can switch this implementation to live charges. */
@Component
public class PayPalGateway {
    private static final String API = "https://api-m.sandbox.paypal.com";
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    @Value("${omnia.paypal.enabled:false}") private boolean enabled;
    @Value("${omnia.paypal.credentials-file:}") private String file;
    @Value("${omnia.paypal.public-base:https://api.myomniaapp.com}") private String publicBase;
    private String cachedToken,cachedClient;
    private long tokenExpires;
    public PayPalGateway(ObjectMapper json) { this.json = json; }
    private JsonNode credentials() {
        try { return json.readTree(Files.readString(Path.of(file))); }
        catch (Exception ex) { throw new IllegalStateException("PayPal Sandbox is not configured"); }
    }
    public boolean ready() {
        if (!enabled || file == null || file.isBlank()) return false;
        try { JsonNode c=credentials(); return !c.path("clientId").asText().isBlank()
                && !c.path("clientSecret").asText().isBlank() && c.path("merchantId").asText().matches("[A-Z0-9]{5,32}")
                && !c.path("webhookId").asText().isBlank() && publicBase.matches("https://[A-Za-z0-9.-]+(?::[0-9]+)?"); }
        catch (Exception ex) { return false; }
    }
    public void requireReady() { if (!ready()) throw new IllegalStateException("PayPal Sandbox is not configured"); }
    public String merchant() { requireReady(); return credentials().path("merchantId").asText(); }
    protected synchronized String token() {
        requireReady(); JsonNode c=credentials();
        String client=c.path("clientId").asText();
        if(client.equals(cachedClient) && cachedToken!=null && System.currentTimeMillis()<tokenExpires)return cachedToken;
        String basic=Base64.getEncoder().encodeToString((c.path("clientId").asText()+":"+c.path("clientSecret").asText()).getBytes(StandardCharsets.UTF_8));
        JsonNode result=exchange(HttpRequest.newBuilder(URI.create(API+"/v1/oauth2/token"))
                .header("Authorization","Basic "+basic).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")));
        String token=result.path("access_token").asText();
        if(token.isBlank()) throw new IllegalStateException("PayPal authentication failed");
        cachedToken=token;cachedClient=client;
        tokenExpires=System.currentTimeMillis()+Math.max(0,Math.min(3600,result.path("expires_in").asLong(300))-60)*1000;
        return token;
    }
    public JsonNode create(String id, BigDecimal total) {
        String root=publicBase+"/api/paypal/return";
        Map<String,Object> body=Map.of("intent","CAPTURE","purchase_units",List.of(Map.of(
                "reference_id",id,"custom_id",id,"payee",Map.of("merchant_id",merchant()),
                "amount",Map.of("currency_code","EUR","value",total.toPlainString()))),
                "payment_source",Map.of("paypal",Map.of("experience_context",Map.of(
                        "brand_name","Omnia Sandbox","shipping_preference","NO_SHIPPING","user_action","PAY_NOW",
                        "return_url",root,"cancel_url",root+"?cancelled=true"))));
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
    public static String approval(JsonNode node) {
        for(JsonNode link:node.path("links")) if(Set.of("approve","payer-action").contains(link.path("rel").asText())) {
            String url=link.path("href").asText();
            try { URI u=URI.create(url); if("https".equals(u.getScheme()) && "www.sandbox.paypal.com".equals(u.getHost())
                    && u.getUserInfo()==null && u.getPort()==-1) return url; } catch(Exception ignored) {}
        }
        throw new IllegalStateException("PayPal approval link unavailable");
    }
    private JsonNode request(String method,String path,Object body,String key) {
        try {
            HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(API+path)).header("Authorization","Bearer "+token())
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
        private static String safe(String value) {
            return value!=null && value.matches("[A-Za-z0-9_]{1,80}")?value:"UNKNOWN";
        }
        public boolean stopCapture() {
            return Set.of("COMPLIANCE_VIOLATION","MAX_NUMBER_OF_PAYMENT_ATTEMPTS_EXCEEDED").contains(issue);
        }
    }
    private JsonNode exchange(HttpRequest.Builder b) {
        try {
            HttpResponse<String> r=http.send(b.timeout(Duration.ofSeconds(18)).header("Accept","application/json").build(),HttpResponse.BodyHandlers.ofString());
            if(r.body().length()>262144)throw new IllegalStateException("PayPal response too large");
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
