package com.omnia.backend.event;

/** A platform admin approved or rejected a seller application; emailed to the applicant after commit. */
public record SellerApplicationDecidedEvent(
        Long applicationId,
        String recipientEmail,
        String recipientName,
        String storeName,
        boolean approved,
        String reason
) {
}
