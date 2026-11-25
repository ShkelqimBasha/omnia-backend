package com.omnia.backend.dto.response;

import com.omnia.backend.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One order Omnia owes the seller (PayPal paid, delivered, not paid out). */
public record PayoutEligibleOrderResponse(
        Long orderId,
        String checkoutReference,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime deliveredAt,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod
) {
}
