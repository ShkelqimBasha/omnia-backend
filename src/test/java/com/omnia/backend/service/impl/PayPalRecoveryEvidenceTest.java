package com.omnia.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omnia.backend.service.impl.PayPalRecoveryEvidence.Action;
import com.omnia.backend.service.impl.PayPalRecoveryEvidence.Snapshot;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayPalRecoveryEvidenceTest {
    private static final String REF = "11111111-2222-4333-8444-555555555555";
    private static final BigDecimal AMOUNT = new BigDecimal("35.50");
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode order(String status, String captureStatus) {
        ObjectNode n = json.createObjectNode().put("id", "PROVIDER1").put("intent", "CAPTURE").put("status", status);
        n.putObject("payer").put("email_address", "buyer@private.example").putObject("name").put("given_name", "Secret");
        n.putArray("links").addObject().put("rel", "approve").put("href", "https://www.sandbox.paypal.com/checkoutnow?token=TOKENX");
        ObjectNode unit = n.putArray("purchase_units").addObject().put("reference_id", REF);
        unit.putObject("payee").put("merchant_id", "MERCHANT1").put("email_address", "merchant@private.example");
        unit.putObject("amount").put("currency_code", "EUR").put("value", "35.50");
        unit.putObject("shipping").putObject("address").put("address_line_1", "Rruga e Fshehte 1");
        if (captureStatus != null) {
            ObjectNode c = unit.putObject("payments").putArray("captures").addObject().put("id", "CAPTURE1")
                    .put("status", captureStatus).put("final_capture", true).put("create_time", "2026-11-02T10:00:00Z");
            c.putObject("amount").put("currency_code", "EUR").put("value", "35.50");
        }
        return n;
    }

    private Snapshot evidence(String status, String captureStatus) {
        return PayPalRecoveryEvidence.from(order(status, captureStatus), "PROVIDER1", REF, "MERCHANT1", AMOUNT);
    }

    private static Set<Action> allowed(Snapshot s) {
        return PayPalRecoveryEvidence.allowed(s, false, false, true);
    }

    @Test void notFoundAccessDeniedAndUnavailableAreUnresolvedNeverUnpaid() {
        PayPalGateway gateway = mock(PayPalGateway.class);
        when(gateway.environment()).thenReturn("sandbox");
        when(gateway.merchant()).thenReturn("MERCHANT1");
        doThrow(new PayPalGateway.Unavailable("NOT_FOUND", 404, "dbg404")).when(gateway).get("PROVIDER1");
        Snapshot notFound = PayPalRecoveryEvidence.fetch(gateway, "sandbox", "PROVIDER1", true, REF, AMOUNT);
        assertEquals("NOT_FOUND", notFound.availability());
        assertTrue(allowed(notFound).isEmpty(), "a 404 must never permit abandoning or refunding");

        doThrow(new PayPalGateway.Unavailable("ACCESS_DENIED", 403, null)).when(gateway).get("PROVIDER1");
        assertTrue(allowed(PayPalRecoveryEvidence.fetch(gateway, "sandbox", "PROVIDER1", true, REF, AMOUNT)).isEmpty());

        doThrow(new IllegalStateException("PayPal unavailable; check payment status")).when(gateway).get("PROVIDER1");
        Snapshot down = PayPalRecoveryEvidence.fetch(gateway, "sandbox", "PROVIDER1", true, REF, AMOUNT);
        assertEquals("UNAVAILABLE", down.availability());
        assertTrue(allowed(down).isEmpty());

        doThrow(new IllegalStateException("PayPal is not configured")).when(gateway).merchant();
        assertTrue(allowed(PayPalRecoveryEvidence.fetch(gateway, "sandbox", "PROVIDER1", true, REF, AMOUNT)).isEmpty());

        // Not configured at all: unavailable, never an outcome, and PayPal is not asked.
        PayPalGateway off = mock(PayPalGateway.class);
        Snapshot unconfigured = PayPalRecoveryEvidence.fetch(off, "sandbox", "PROVIDER1", true, REF, AMOUNT);
        assertEquals("UNAVAILABLE", unconfigured.availability());
        verify(off, never()).get(anyString());
    }

    @Test void checkoutOfTheOtherEnvironmentIsNeverSentToPayPal() {
        for (String[] pair : new String[][]{{"live", "sandbox"}, {"sandbox", "live"}, {"live", "unknown"}, {"live", null}}) {
            PayPalGateway gateway = mock(PayPalGateway.class);
            when(gateway.environment()).thenReturn(pair[0]);
            Snapshot other = PayPalRecoveryEvidence.fetch(gateway, pair[1], "PROVIDER1", true, REF, AMOUNT);
            assertEquals("OTHER_ENVIRONMENT", other.availability(), String.join("/", String.valueOf(pair[0]), String.valueOf(pair[1])));
            assertTrue(other.detail().contains("this server uses PayPal " + pair[0]), other.detail());
            assertTrue(allowed(other).isEmpty(), "evidence from no PayPal permits no action");
            assertEquals(EnumSet.of(Action.FLAG_MANUAL), PayPalRecoveryEvidence.allowed(other, true, false, true));
            verify(gateway, never()).get(anyString());
            verify(gateway, never()).merchant();
        }
        // A checkout that never reached PayPal needs no provider either way: positive evidence stays usable.
        PayPalGateway live = mock(PayPalGateway.class);
        when(live.environment()).thenReturn("live");
        assertEquals("NOT_CREATED", PayPalRecoveryEvidence.fetch(live, "sandbox", null, false, REF, AMOUNT).availability());
        verifyNoInteractions(live);
    }

    @Test void onlyVerifiedOutcomesPermitActions() {
        assertEquals(EnumSet.of(Action.CONFIRM_PAID), allowed(evidence("COMPLETED", "COMPLETED")));
        assertEquals(EnumSet.of(Action.CONFIRM_REFUNDED), allowed(evidence("COMPLETED", "REFUNDED")));
        assertEquals(EnumSet.of(Action.ABANDON), allowed(evidence("CREATED", null)));
        assertEquals(EnumSet.of(Action.ABANDON), allowed(evidence("APPROVED", null)),
                "approved but uncaptured is abandoned, never captured");
        assertEquals(EnumSet.of(Action.ABANDON), allowed(evidence("COMPLETED", "DECLINED")));
        assertTrue(allowed(evidence("COMPLETED", "PENDING")).isEmpty(), "pending capture waits");
        assertTrue(allowed(evidence("COMPLETED", "PARTIALLY_REFUNDED")).isEmpty(), "partial refund needs manual review");
        assertEquals(EnumSet.of(Action.ABANDON), allowed(PayPalRecoveryEvidence.notCreated()));
    }

    @Test void missingProviderIdAfterACreateAttemptIsUnresolvedNotNotCreated() {
        PayPalGateway gateway = mock(PayPalGateway.class);
        for (String missing : new String[]{null, "", " "}) {
            Snapshot uncertain = PayPalRecoveryEvidence.fetch(gateway, "sandbox", missing, true, REF, AMOUNT);
            assertEquals("CREATION_UNCERTAIN", uncertain.availability(), String.valueOf(missing));
            assertFalse(uncertain.available());
            assertNull(uncertain.outcome());
            assertTrue(allowed(uncertain).isEmpty(), "a missing local ID never permits ABANDON");
            assertEquals(uncertain.fingerprint(), PayPalRecoveryEvidence.creationUncertain().fingerprint(),
                    "the fingerprint is stable, so a resolve request can reference it");
        }
        assertNotEquals(PayPalRecoveryEvidence.notCreated().fingerprint(),
                PayPalRecoveryEvidence.creationUncertain().fingerprint());
        // Shipped orders can still be flagged for manual handling while creation is uncertain.
        assertEquals(EnumSet.of(Action.FLAG_MANUAL),
                PayPalRecoveryEvidence.allowed(PayPalRecoveryEvidence.creationUncertain(), true, false, true));
        // Without a stored ID there is nothing to ask PayPal: no provider call either way.
        verifyNoInteractions(gateway);
    }

    @Test void onlyACheckoutThatNeverSentACreateRequestMayBeAbandonedWithoutProviderData() {
        assertEquals("NOT_CREATED", PayPalRecoveryEvidence.withoutProviderId(false).availability());
        assertEquals(EnumSet.of(Action.ABANDON), allowed(PayPalRecoveryEvidence.withoutProviderId(false)));
        assertEquals("CREATION_UNCERTAIN", PayPalRecoveryEvidence.withoutProviderId(true).availability());
        assertTrue(allowed(PayPalRecoveryEvidence.withoutProviderId(true)).isEmpty());
    }

    @Test void mismatchedEvidenceIsUnresolved() {
        Snapshot other = PayPalRecoveryEvidence.from(order("COMPLETED", "COMPLETED"), "PROVIDER1", REF, "OTHERMERCHANT", AMOUNT);
        assertEquals("MISMATCH", other.availability());
        assertTrue(allowed(other).isEmpty());
        Snapshot amount = PayPalRecoveryEvidence.from(order("COMPLETED", "COMPLETED"), "PROVIDER1", REF, "MERCHANT1", new BigDecimal("1.00"));
        assertTrue(allowed(amount).isEmpty());
    }

    @Test void shippedOrdersOnlyAllowManualFlaggingAndChangedGroupsNothing() {
        Snapshot paid = evidence("COMPLETED", "COMPLETED");
        assertEquals(EnumSet.of(Action.FLAG_MANUAL), PayPalRecoveryEvidence.allowed(paid, true, false, true));
        assertEquals(EnumSet.of(Action.FLAG_MANUAL),
                PayPalRecoveryEvidence.allowed(PayPalRecoveryEvidence.unknown(PayPalRecoveryEvidence.Availability.NOT_FOUND, "x"), true, false, true),
                "shipped orders can be flagged even while evidence is unavailable");
        assertTrue(PayPalRecoveryEvidence.allowed(paid, false, true, true).isEmpty());
        assertTrue(PayPalRecoveryEvidence.allowed(paid, false, false, false).isEmpty());
    }

    @Test void snapshotKeepsMoneyFactsAndDropsPersonalData() throws Exception {
        Snapshot paid = evidence("COMPLETED", "COMPLETED");
        assertEquals("CAPTURE1", paid.captureId());
        assertEquals("35.50", paid.amount());
        assertEquals("EUR", paid.currency());
        assertEquals("2026-11-02T10:00:00Z", paid.captureTime());
        String serialized = json.writeValueAsString(paid);
        for (String secret : new String[]{"buyer@private.example", "Secret", "TOKENX", "sandbox.paypal.com",
                "merchant@private.example", "Rruga", "MERCHANT1"})
            assertFalse(serialized.contains(secret), "evidence leaked " + secret);
    }

    @Test void malformedProviderValuesAreDroppedNotEchoed() {
        ObjectNode node = (ObjectNode) order("COMPLETED", "COMPLETED");
        ObjectNode capture = (ObjectNode) node.path("purchase_units").path(0).path("payments").path("captures").path(0);
        capture.put("create_time", "not a time").put("status", "<script>");
        Snapshot snapshot = PayPalRecoveryEvidence.from(node, "PROVIDER1", REF, "MERCHANT1", AMOUNT);
        assertNull(snapshot.captureTime());
        assertNull(snapshot.captureStatus());
        Snapshot uncaptured = evidence("APPROVED", null);
        assertNull(uncaptured.captureId());
        assertNull(uncaptured.finalCapture());
        assertEquals("35.50", uncaptured.amount());
        assertEquals("NOT_CREATED", PayPalRecoveryEvidence.fetch(mock(PayPalGateway.class), "sandbox", " ", false, REF, AMOUNT).availability());
        assertThrows(IllegalArgumentException.class,
                () -> PayPalRecoveryEvidence.unknown(PayPalRecoveryEvidence.Availability.AVAILABLE, "x"));
        Snapshot noOutcome = new Snapshot("AVAILABLE", null, null, null, null, null, null, null, null, null, null);
        assertTrue(allowed(noOutcome).isEmpty());
    }

    @Test void fingerprintIsStableAndChangesWithEvidence() {
        assertEquals(evidence("APPROVED", null).fingerprint(), evidence("APPROVED", null).fingerprint());
        assertNotEquals(evidence("APPROVED", null).fingerprint(), evidence("COMPLETED", "COMPLETED").fingerprint());
        assertNotEquals(evidence("COMPLETED", "COMPLETED").fingerprint(), evidence("COMPLETED", "REFUNDED").fingerprint());
        assertTrue(evidence("APPROVED", null).fingerprint().matches("[0-9a-f]{64}"));
    }

    @Test void closedOrReviewedCheckoutsAreNeverCapturable() {
        for (String state : new String[]{"ABANDONED", "REFUNDED", "REVIEW", "RECOVERY_REQUIRED", "PAID", "BLOCKED", "EXHAUSTED"})
            assertFalse(PayPalCheckoutService.CAPTURABLE.contains(state), state);
        assertTrue(PayPalCheckoutService.CAPTURABLE.contains("APPROVED"));
    }
}
