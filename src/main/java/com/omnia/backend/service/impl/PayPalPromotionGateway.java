package com.omnia.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Separate API client for Omnia's own promotion fees. No cart or seller payouts. */
@Component
public final class PayPalPromotionGateway {
    private static final String LIVE = "https://api-m.paypal.com";
    private static final String SANDBOX = "https://api-m.sandbox.paypal.com";
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ObjectMapper json;
    private final boolean enabled;
    private final boolean live;
    private final String credentialsFile;

    public PayPalPromotionGateway(
            ObjectMapper json,
            @Value("${omnia.promotions.paypal.enabled:false}") boolean enabled,
            @Value("${omnia.promotions.paypal.live:false}") boolean live,
            @Value("${omnia.promotions.paypal.credentials-file:}") String credentialsFile) {
        this.json = json;
        this.enabled = enabled;
        this.live = live;
        this.credentialsFile = credentialsFile;
    }

    record Approval(String providerOrderId, URI url, String merchantId) {}

    Approval create(UUID reference, BigDecimal amount) {
        if (reference == null || amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Invalid promotion payment");
        }
        Credentials credentials = credentials();
        String ref = reference.toString();
        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "reference_id", ref,
                        "custom_id", ref,
                        "description", "Omnia Listing Promotion",
                        "payee", Map.of("merchant_id", credentials.merchantId()),
                        "amount", Map.of("currency_code", "EUR", "value", amount.toPlainString()))),
                "payment_source", Map.of("paypal", Map.of("experience_context", Map.of(
                        "brand_name", "Omnia",
                        "shipping_preference", "NO_SHIPPING",
                        "user_action", "PAY_NOW",
                        "return_url", "https://api.myomniaapp.com/api/promotions/paypal/return",
                        "cancel_url", "https://api.myomniaapp.com/api/promotions/paypal/return?cancelled=true"))));
        JsonNode response = request(credentials, "/v2/checkout/orders", body, ref);
        return new Approval(validId(response.path("id").asText()), approvalUrl(response, live),
                credentials.merchantId());
    }

    PayPalEvidence.Result inspect(String orderId, UUID reference, String merchantId, BigDecimal amount) {
        return evidence(orderId, reference, merchantId, amount, false);
    }

    // The caller must persist and own the attempt before allowing capture.
    PayPalEvidence.Result capture(String orderId, UUID reference, String merchantId, BigDecimal amount) {
        return evidence(orderId, reference, merchantId, amount, true);
    }

    private PayPalEvidence.Result evidence(String orderId, UUID reference, String merchantId,
                                           BigDecimal amount, boolean capture) {
        String path = "/v2/checkout/orders/" + validId(orderId);
        Credentials credentials = credentials();
        if (!credentials.merchantId().equals(merchantId)) {
            throw new IllegalStateException("Promotion merchant changed; review the payment");
        }
        JsonNode order = request(credentials, path, null, null);
        var evidence = PayPalEvidence.verify(order, orderId, reference.toString(), merchantId, amount);
        if (!capture || !"APPROVED".equals(evidence.state())) return evidence;
        request(credentials, path + "/capture", Map.of(), orderId);
        return PayPalEvidence.verify(request(credentials, path, null, null),
                orderId, reference.toString(), merchantId, amount);
    }

    private record Credentials(String id, String secret, String merchantId) {
        @Override public String toString() { return "[PayPal promotion credentials]"; }
    }

    private Credentials credentials() {
        if (!enabled || credentialsFile == null || credentialsFile.isBlank()) {
            throw new IllegalStateException("PayPal promotions are disabled");
        }
        try (var reader = Files.newBufferedReader(Path.of(credentialsFile), StandardCharsets.UTF_8)) {
            JsonNode value = json.readTree(reader);
            String id = value.path("clientId").asText();
            String secret = value.path("clientSecret").asText();
            String merchant = value.path("merchantId").asText();
            if (id.isBlank() || secret.isBlank() || !merchant.matches("[A-Z0-9]{5,32}")) {
                throw new IllegalArgumentException();
            }
            return new Credentials(id, secret, merchant);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("PayPal promotion credentials are not configured");
        }
    }

    private JsonNode request(Credentials credentials, String path, Map<String, Object> body, String key) {
        String base = live ? LIVE : SANDBOX;
        String basic = Base64.getEncoder().encodeToString(
                (credentials.id() + ":" + credentials.secret()).getBytes(StandardCharsets.UTF_8));
        JsonNode token = send(HttpRequest.newBuilder(URI.create(base + "/v1/oauth2/token"))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")));
        String accessToken = token.path("access_token").asText();
        if (!accessToken.matches("[A-Za-z0-9._~+/-]+=*")) {
            throw new IllegalStateException("PayPal promotion authentication failed");
        }
        var request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Authorization", "Bearer " + accessToken)
                .header("Prefer", "return=representation");
        if (key != null) request.header("PayPal-Request-Id", key);
        if (body == null) return send(request.GET());
        try {
            return send(request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not prepare the PayPal promotion request");
        }
    }

    private JsonNode send(HttpRequest.Builder request) {
        try {
            var response = http.send(request.timeout(Duration.ofSeconds(18)).header("Accept", "application/json").build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("PayPal promotion request failed: HTTP " + response.statusCode());
                }
                byte[] body = stream.readNBytes(262145);
                if (body.length > 262144) throw new IllegalStateException("PayPal response too large");
                JsonNode value = json.readTree(body);
                if (value == null || !value.isObject()) throw new IllegalStateException("Invalid PayPal response");
                return value;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PayPal promotion request interrupted");
        } catch (IOException ex) {
            throw new IllegalStateException("PayPal promotion request could not be confirmed");
        }
    }

    private static String validId(String id) {
        if (id == null || !id.matches("[A-Z0-9]{5,38}")) {
            throw new IllegalArgumentException("Invalid PayPal promotion order ID");
        }
        return id;
    }

    static URI approvalUrl(JsonNode order, boolean live) {
        String expectedHost = live ? "www.paypal.com" : "www.sandbox.paypal.com";
        for (JsonNode link : order.path("links")) {
            String relation = link.path("rel").asText();
            if (!"approve".equals(relation) && !"payer-action".equals(relation)) continue;
            try {
                URI url = URI.create(link.path("href").asText());
                if ("https".equals(url.getScheme()) && expectedHost.equals(url.getHost())
                        && url.getUserInfo() == null && url.getPort() == -1) return url;
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed links and continue looking for the approval URL.
            }
        }
        throw new IllegalStateException("PayPal promotion approval link unavailable");
    }
}
