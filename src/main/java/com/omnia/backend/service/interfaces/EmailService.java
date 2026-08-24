package com.omnia.backend.service.interfaces;
import com.omnia.backend.enums.OrderStatus;

import java.math.BigDecimal;

public interface EmailService {

    void sendEmailVerification(
            String recipientEmail,
            String recipientName,
            String token
    );

    void sendPasswordResetEmail(
            String recipientEmail,
            String recipientName,
            String token
    );
    void sendOrderStatusEmail(
            String recipientEmail,
            String recipientName,
            Long orderId,
            OrderStatus status,
            BigDecimal totalAmount
    );
}