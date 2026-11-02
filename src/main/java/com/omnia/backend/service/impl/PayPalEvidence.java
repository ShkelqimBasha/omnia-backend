package com.omnia.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

/** Only provider evidence with the exact merchant, invoice, currency and gross amount can settle orders. */
public final class PayPalEvidence {
    private PayPalEvidence() {}
    public record Result(String state, String captureId, java.time.Instant paidAt) {
        Result(String state,String captureId){this(state,captureId,null);}
    }
    public static Result verify(JsonNode order, String providerId, String reference,
                                String merchant, BigDecimal amount) {
        require(providerId.equals(order.path("id").asText()), "PayPal order mismatch");
        require("CAPTURE".equals(order.path("intent").asText()), "PayPal intent mismatch");
        JsonNode units = order.path("purchase_units");
        require(units.isArray() && units.size() == 1, "Unexpected PayPal purchase units");
        JsonNode unit = units.get(0);
        require(reference.equals(unit.path("reference_id").asText()), "PayPal reference mismatch");
        require(merchant.equals(unit.path("payee").path("merchant_id").asText()), "PayPal merchant mismatch");
        money(unit.path("amount"), amount);
        JsonNode captures = unit.path("payments").path("captures");
        if (captures.isArray() && !captures.isEmpty()) {
            require(captures.size() == 1, "Unexpected partial captures");
            JsonNode capture = captures.get(0);
            money(capture.path("amount"), amount);
            String id = capture.path("id").asText();
            require(id.matches("[A-Z0-9]{5,64}"), "Invalid capture ID");
            String status = capture.path("status").asText();
            if ("COMPLETED".equals(status)) {
                require("COMPLETED".equals(order.path("status").asText()), "Order not completed");
                require(capture.path("final_capture").asBoolean(), "Capture not final");
                java.time.Instant paidAt;
                try { paidAt=java.time.OffsetDateTime.parse(capture.path("create_time").asText()).toInstant(); }
                catch(Exception ex){throw new IllegalArgumentException("Invalid capture timestamp");}
                return new Result("PAID", id, paidAt);
            }
            if ("REFUNDED".equals(status)) return new Result("REFUNDED", id);
            // A partial refund must not be reported as fully paid or fully refunded.
            if ("PARTIALLY_REFUNDED".equals(status)) return new Result("REVIEW", id);
            if ("DECLINED".equals(status) || "FAILED".equals(status)) return new Result("FAILED", id);
            return new Result("PENDING", id);
        }
        return new Result(switch (order.path("status").asText()) {
            case "APPROVED" -> "APPROVED";
            case "CREATED", "PAYER_ACTION_REQUIRED" -> "APPROVAL";
            case "VOIDED" -> "FAILED";
            default -> "PENDING";
        }, null);
    }
    private static void money(JsonNode node, BigDecimal expected) {
        require("EUR".equals(node.path("currency_code").asText()), "PayPal currency mismatch");
        try { require(new BigDecimal(node.path("value").asText()).compareTo(expected) == 0, "PayPal amount mismatch"); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid PayPal amount"); }
    }
    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
