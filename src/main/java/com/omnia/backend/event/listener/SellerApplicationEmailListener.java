package com.omnia.backend.event.listener;

import com.omnia.backend.event.SellerApplicationDecidedEvent;
import com.omnia.backend.service.interfaces.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Emails the applicant the admin's decision, only after it is committed. */
@Component
public class SellerApplicationEmailListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SellerApplicationEmailListener.class);

    private final EmailService emailService;
    private final boolean enabled;

    public SellerApplicationEmailListener(EmailService emailService,
                                          @Value("${app.seller-applications.email.enabled:true}") boolean enabled) {
        this.emailService = emailService;
        this.enabled = enabled;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecision(SellerApplicationDecidedEvent event) {
        if (!enabled || event.recipientEmail() == null || event.recipientEmail().isBlank()) return;
        try {
            emailService.sendSellerApplicationDecisionEmail(event);
        } catch (Exception exception) {
            // The decision is saved and in the applicant's inbox; a mail outage must not fail the admin.
            LOGGER.error("Failed to send the seller application email for application {}",
                    event.applicationId(), exception);
        }
    }
}
