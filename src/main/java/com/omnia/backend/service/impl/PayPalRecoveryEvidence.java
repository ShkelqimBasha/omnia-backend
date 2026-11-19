package com.omnia.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Sanitized PayPal evidence for recovery decisions. Only fixed fields that describe the money
 * (status, capture ID, amount, currency, time) leave this class: never payer identity, address,
 * approval links, tokens or raw provider payloads. Missing evidence of any kind is "unknown" and
 * never permits an action that assumes the buyer did not pay.
 */
public final class PayPalRecoveryEvidence {
    private PayPalRecoveryEvidence() {}

    public enum Availability {
        /** Provider evidence matched this checkout (merchant, reference, currency, amount). */
        AVAILABLE,
        /** We never stored a PayPal order ID and never issued an approval link. */
        NOT_CREATED,
        /** PayPal answered 404 with the current credentials. Not proof of non-payment. */
        NOT_FOUND,
        /** PayPal refused our credentials (401/403). Not proof of non-payment. */
        ACCESS_DENIED,
        /** Timeout, network failure, misconfiguration or another provider error. */
        UNAVAILABLE,
        /** PayPal returned an order that does not match this checkout. */
        MISMATCH
    }

    public enum Action { CONFIRM_PAID, CONFIRM_REFUNDED, ABANDON, FLAG_MANUAL }

    /** Everything an admin response or audit row may contain about PayPal's view. */
    public record Snapshot(String availability, String outcome, String orderStatus, String captureId,
                           String captureStatus, String amount, String currency, Boolean finalCapture,
                           String captureTime, String detail, String debugId) {
        public boolean available() { return Availability.AVAILABLE.name().equals(availability); }

        /**
         * Stable over identical evidence, different whenever any recorded fact changes. PayPal's
         * debug_id differs on every request, so it is kept for support but never fingerprinted.
         */
        public String fingerprint() {
            String canonical = String.join("|", Arrays.asList(availability, outcome, orderStatus, captureId,
                    captureStatus, amount, currency, String.valueOf(finalCapture), captureTime, detail));
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(canonical.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception ex) {
                throw new IllegalStateException("SHA-256 unavailable", ex);
            }
        }
    }

    static Snapshot notCreated() {
        return new Snapshot(Availability.NOT_CREATED.name(), null, null, null, null, null, null, null, null,
                "No PayPal order was recorded and no approval link was issued", null);
    }

    static Snapshot unknown(Availability availability, String detail) {
        return unknown(availability, detail, null);
    }

    static Snapshot unknown(Availability availability, String detail, String debugId) {
        if (availability == Availability.AVAILABLE) throw new IllegalArgumentException("Not an unknown state");
        return new Snapshot(availability.name(), null, null, null, null, null, null, null, null, detail, debugId);
    }

    /** Read provider truth; failures become an explicit unknown availability, never an outcome. */
    public static Snapshot fetch(PayPalGateway gateway, String providerOrderId, String reference, BigDecimal amount) {
        if (providerOrderId == null || providerOrderId.isBlank()) return notCreated();
        try {
            String merchant = gateway.merchant();
            return from(gateway.get(providerOrderId), providerOrderId, reference, merchant, amount);
        } catch (PayPalGateway.Unavailable unavailable) {
            Availability kind = "NOT_FOUND".equals(unavailable.kind) ? Availability.NOT_FOUND : Availability.ACCESS_DENIED;
            return unknown(kind, "PayPal HTTP " + unavailable.status, unavailable.debugId);
        } catch (RuntimeException failure) {
            return unknown(Availability.UNAVAILABLE, "PayPal evidence could not be read");
        }
    }

    static Snapshot from(JsonNode order, String providerOrderId, String reference, String merchant, BigDecimal amount) {
        PayPalEvidence.Result result;
        try {
            result = PayPalEvidence.verify(order, providerOrderId, reference, merchant, amount);
        } catch (IllegalArgumentException mismatch) {
            // Messages come from PayPalEvidence's fixed texts, never from the provider payload.
            return unknown(Availability.MISMATCH, safeText(mismatch.getMessage()));
        }
        JsonNode capture = order.path("purchase_units").path(0).path("payments").path("captures").path(0);
        boolean hasCapture = !capture.isMissingNode() && capture.isObject();
        String time = null;
        if (hasCapture) {
            try { time = OffsetDateTime.parse(capture.path("create_time").asText()).toInstant().toString(); }
            catch (Exception ignored) { time = null; }
        }
        JsonNode money = hasCapture ? capture.path("amount") : order.path("purchase_units").path(0).path("amount");
        return new Snapshot(Availability.AVAILABLE.name(), result.state(),
                token(order.path("status").asText(null)),
                hasCapture ? id(capture.path("id").asText(null)) : null,
                hasCapture ? token(capture.path("status").asText(null)) : null,
                decimal(money.path("value").asText(null)),
                currency(money.path("currency_code").asText(null)),
                hasCapture ? capture.path("final_capture").asBoolean() : null,
                time, null, null);
    }

    /**
     * Actions the evidence permits. Empty means unresolved: an admin cannot change the checkout.
     * Shipped or delivered orders only allow flagging for manual handling.
     */
    public static Set<Action> allowed(Snapshot evidence, boolean shippedOrDelivered, boolean anyCancelled,
                                      boolean totalsMatch) {
        if (shippedOrDelivered) return EnumSet.of(Action.FLAG_MANUAL);
        if (anyCancelled || !totalsMatch) return EnumSet.noneOf(Action.class);
        if (Availability.NOT_CREATED.name().equals(evidence.availability())) return EnumSet.of(Action.ABANDON);
        if (!evidence.available()) return EnumSet.noneOf(Action.class);
        String outcome = evidence.outcome() == null ? "" : evidence.outcome();
        switch (outcome) {
            case "PAID": return EnumSet.of(Action.CONFIRM_PAID);
            case "REFUNDED": return EnumSet.of(Action.CONFIRM_REFUNDED);
            // No money moved (or the capture failed). Approved-but-uncaptured is never captured here.
            case "APPROVAL": case "APPROVED": case "FAILED": return EnumSet.of(Action.ABANDON);
            // PENDING capture or partial refund (REVIEW): wait for PayPal or handle manually.
            default: return EnumSet.noneOf(Action.class);
        }
    }

    private static String id(String value) {
        return value != null && value.matches("[A-Z0-9]{5,64}") ? value : null;
    }
    private static String token(String value) {
        return value != null && value.matches("[A-Z_]{1,40}") ? value : null;
    }
    private static String decimal(String value) {
        return value != null && value.matches("\\d{1,10}(\\.\\d{1,2})?") ? value : null;
    }
    private static String currency(String value) {
        return value != null && value.matches("[A-Z]{3}") ? value : null;
    }
    private static String safeText(String value) {
        return value != null && value.matches("[A-Za-z ;:_-]{1,80}") ? value : "PayPal evidence mismatch";
    }
}
