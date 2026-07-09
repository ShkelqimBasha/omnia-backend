package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.PaymentResponse;
import com.omnia.backend.entity.Payment;

public class PaymentMapper {

    public static PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder().getId())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private PaymentMapper() {
    }
}