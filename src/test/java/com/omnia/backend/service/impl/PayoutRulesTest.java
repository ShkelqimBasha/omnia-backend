package com.omnia.backend.service.impl;

import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayoutRulesTest {

    private static boolean eligible(OrderStatus order, PaymentMethod method, PaymentStatus payment) {
        return PayoutRules.eligible(1L, null, order, method, payment, "PAID");
    }

    @Test
    void paypalPaidAndDeliveredIsOwed() {
        assertTrue(eligible(OrderStatus.DELIVERED, PaymentMethod.PAYPAL, PaymentStatus.SUCCESS));
        assertTrue(PayoutRules.eligible(1L, null, OrderStatus.DELIVERED, PaymentMethod.PAYPAL,
                PaymentStatus.SUCCESS, null));
    }

    @Test
    void cashOnDeliveryIsNeverOwed() {
        assertFalse(eligible(OrderStatus.DELIVERED, PaymentMethod.CASH_ON_DELIVERY, PaymentStatus.SUCCESS));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"CARD", "GOOGLE_PAY"})
    void otherMethodsAreNotOwed(PaymentMethod method) {
        assertFalse(eligible(OrderStatus.DELIVERED, method, PaymentStatus.SUCCESS));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "DELIVERED", mode = EnumSource.Mode.EXCLUDE)
    void undeliveredOrdersAreNotOwed(OrderStatus status) {
        assertFalse(eligible(status, PaymentMethod.PAYPAL, PaymentStatus.SUCCESS));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "SUCCESS", mode = EnumSource.Mode.EXCLUDE)
    void unpaidOrRefundedPaymentsAreNotOwed(PaymentStatus status) {
        assertFalse(eligible(OrderStatus.DELIVERED, PaymentMethod.PAYPAL, status));
    }

    @Test
    void refundedOrPartiallyRefundedCheckoutIsNotOwed() {
        for (String state : new String[]{"REFUNDED", "REVIEW"}) {
            assertFalse(PayoutRules.eligible(1L, null, OrderStatus.DELIVERED, PaymentMethod.PAYPAL,
                    PaymentStatus.SUCCESS, state));
        }
    }

    @Test
    void paidOutOrSellerlessOrdersAreNotOwed() {
        assertFalse(PayoutRules.eligible(1L, 9L, OrderStatus.DELIVERED, PaymentMethod.PAYPAL, PaymentStatus.SUCCESS, "PAID"));
        assertFalse(PayoutRules.eligible(null, null, OrderStatus.DELIVERED, PaymentMethod.PAYPAL, PaymentStatus.SUCCESS, "PAID"));
    }

    @Test
    void noCommissionPaysTheWholeGross() {
        PayoutRules.Amounts a = PayoutRules.amounts(new BigDecimal("123.4"), PayoutRules.commissionPercent(BigDecimal.ZERO));
        assertEquals(new BigDecimal("123.40"), a.gross());
        assertEquals(new BigDecimal("0.00"), a.commission());
        assertEquals(new BigDecimal("123.40"), a.net());
    }

    @Test
    void commissionIsRoundedHalfUpToCents() {
        // 10% of 10.05 = 1.005 -> 1.01
        PayoutRules.Amounts a = PayoutRules.amounts(new BigDecimal("10.05"), new BigDecimal("10"));
        assertEquals(new BigDecimal("1.01"), a.commission());
        assertEquals(new BigDecimal("9.04"), a.net());
        // 7.5% of 33.33 = 2.49975 -> 2.50
        PayoutRules.Amounts b = PayoutRules.amounts(new BigDecimal("33.33"), new BigDecimal("7.5"));
        assertEquals(new BigDecimal("2.50"), b.commission());
        assertEquals(new BigDecimal("30.83"), b.net());
        assertEquals(b.gross(), b.commission().add(b.net()));
    }

    @Test
    void emptyGrossIsZero() {
        PayoutRules.Amounts a = PayoutRules.amounts(null, new BigDecimal("5"));
        assertEquals(new BigDecimal("0.00"), a.gross());
        assertEquals(new BigDecimal("0.00"), a.net());
    }

    @Test
    void commissionPercentMustBeBetweenZeroAndFifty() {
        assertEquals(new BigDecimal("0.00"), PayoutRules.commissionPercent(null));
        assertEquals(new BigDecimal("50.00"), PayoutRules.commissionPercent(new BigDecimal("50")));
        assertEquals(new BigDecimal("12.50"), PayoutRules.commissionPercent(new BigDecimal("12.5")));
        assertThrows(IllegalStateException.class, () -> PayoutRules.commissionPercent(new BigDecimal("-0.01")));
        assertThrows(IllegalStateException.class, () -> PayoutRules.commissionPercent(new BigDecimal("50.01")));
    }

    private static List<BigDecimal> money(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }

    @Test
    void adjustmentIsTheRefundedPartLessTheCommissionOfThePayout() {
        assertEquals(new BigDecimal("12.30"), PayoutRules.adjustmentAmount(new BigDecimal("12.30"), new BigDecimal("0.00")));
        // 10% of 12.35 = 1.235 → 1.24 commission kept by Omnia, 11.11 to give back.
        assertEquals(new BigDecimal("11.11"), PayoutRules.adjustmentAmount(new BigDecimal("12.35"), new BigDecimal("10.00")));
    }

    @Test
    void fullRefundGivesEveryOrderItsTotal() {
        assertEquals(money("10.00", "30.00"), PayoutRules.refundShares(new BigDecimal("40.00"), money("10.00", "30.00")));
        // More than the checkout (cannot happen at PayPal) is capped.
        assertEquals(money("10.00", "30.00"), PayoutRules.refundShares(new BigDecimal("99.00"), money("10.00", "30.00")));
    }

    @Test
    void partialRefundIsSplitByOrderValueAndAddsUpToTheCent() {
        assertEquals(money("2.50", "7.50"), PayoutRules.refundShares(new BigDecimal("10.00"), money("10.00", "30.00")));
        List<BigDecimal> thirds = PayoutRules.refundShares(new BigDecimal("10.00"), money("10.00", "10.00", "10.00"));
        assertEquals(money("3.33", "3.33", "3.34"), thirds);
        assertEquals(money("5.00"), PayoutRules.refundShares(new BigDecimal("5.00"), money("12.30")));
        assertEquals(money("0.00", "0.00"), PayoutRules.refundShares(BigDecimal.ZERO, money("1.00", "2.00")));
    }

    @Test
    void payoutDeductsOpenAdjustmentsOldestFirstAndNeverMoreThanItsNet() {
        assertEquals(money("5.00", "3.00"), PayoutRules.deductions(money("5.00", "3.00"), new BigDecimal("20.00")));
        // 6.00 available: the first fully, the second in part, the third waits.
        assertEquals(money("5.00", "1.00", "0.00"),
                PayoutRules.deductions(money("5.00", "3.00", "2.00"), new BigDecimal("6.00")));
        assertEquals(money("0.00"), PayoutRules.deductions(money("5.00"), BigDecimal.ZERO));
    }

    @Test
    void referenceKeyIgnoresCaseAndSpaces() {
        assertEquals("bkt 2026 001", PayoutRules.referenceKey("  BKT   2026 001 "));
        assertNull(PayoutRules.referenceKey("   "));
        assertNull(PayoutRules.referenceKey(null));
    }

    private static JsonNode paypalOrder(String captureStatus, String refunds) throws Exception {
        return new ObjectMapper().readTree("{\"purchase_units\":[{\"payments\":{\"captures\":[{\"status\":\""
                + captureStatus + "\"}],\"refunds\":" + refunds + "}}]}");
    }

    @Test
    void refundedTotalIsReadFromPayPalEvidence() throws Exception {
        BigDecimal amount = new BigDecimal("40.00");
        assertEquals(amount, PayPalEvidence.refundedTotal(paypalOrder("REFUNDED", "[]"), amount));
        assertEquals(new BigDecimal("7.50"), PayPalEvidence.refundedTotal(paypalOrder("PARTIALLY_REFUNDED",
                "[{\"status\":\"COMPLETED\",\"amount\":{\"currency_code\":\"EUR\",\"value\":\"5.00\"}},"
                        + "{\"status\":\"PENDING\",\"amount\":{\"currency_code\":\"EUR\",\"value\":\"9.00\"}},"
                        + "{\"status\":\"COMPLETED\",\"amount\":{\"currency_code\":\"EUR\",\"value\":\"2.50\"}}]"), amount));
        // Unknown amounts are never guessed.
        assertNull(PayPalEvidence.refundedTotal(paypalOrder("PARTIALLY_REFUNDED", "[]"), amount));
        assertNull(PayPalEvidence.refundedTotal(paypalOrder("PARTIALLY_REFUNDED",
                "[{\"status\":\"COMPLETED\",\"amount\":{\"currency_code\":\"USD\",\"value\":\"5.00\"}}]"), amount));
        assertNull(PayPalEvidence.refundedTotal(paypalOrder("COMPLETED", "[]"), amount));
    }
}
