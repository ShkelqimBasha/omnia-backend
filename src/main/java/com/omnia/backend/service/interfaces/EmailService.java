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

    /** A new "Kontakto" / "Kërko vizitë" request, to the company. */
    void sendInquiryEmail(
            String recipientEmail,
            com.omnia.backend.event.InquiryCreatedEvent inquiry
    );

    /** "Aplikimi u pranua" / "Aplikimi u refuzua", to the seller applicant. */
    void sendSellerApplicationDecisionEmail(
            com.omnia.backend.event.SellerApplicationDecidedEvent decision
    );
}
