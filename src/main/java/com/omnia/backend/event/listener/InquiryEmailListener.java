package com.omnia.backend.event.listener;

import com.omnia.backend.event.InquiryCreatedEvent;
import com.omnia.backend.service.interfaces.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Emails the company about a new "Kontakto" / "Kërko vizitë" request, after it is saved. */
@Component
public class InquiryEmailListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(InquiryEmailListener.class);

    private final EmailService emailService;
    private final boolean enabled;

    public InquiryEmailListener(EmailService emailService,
                                @Value("${app.inquiries.company-email.enabled:true}") boolean enabled) {
        this.emailService = emailService;
        this.enabled = enabled;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInquiryCreated(InquiryCreatedEvent event) {
        if (!enabled) return;
        for (String recipient : event.recipientEmails()) {
            try {
                emailService.sendInquiryEmail(recipient, event);
            } catch (Exception exception) {
                // The inquiry is saved and in the seller's inbox; a mail outage must not fail the buyer.
                LOGGER.error("Failed to send the inquiry email for inquiry {}", event.inquiryId(), exception);
            }
        }
    }
}
