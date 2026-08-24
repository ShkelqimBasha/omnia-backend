package com.omnia.backend.event;

import com.omnia.backend.enums.OrderStatus;

import java.math.BigDecimal;

public record OrderStatusChangedEvent(
        String recipientEmail,
        String recipientName,
        Long orderId,
        OrderStatus status,
        BigDecimal totalAmount
) {
}