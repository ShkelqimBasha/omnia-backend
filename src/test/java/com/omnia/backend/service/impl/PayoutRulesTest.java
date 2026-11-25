package com.omnia.backend.service.impl;

import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
