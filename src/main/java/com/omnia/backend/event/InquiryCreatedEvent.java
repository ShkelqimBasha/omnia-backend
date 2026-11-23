package com.omnia.backend.event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Published inside the inquiry's transaction; the company email goes out after commit. */
public record InquiryCreatedEvent(
        Long inquiryId,
        List<String> recipientEmails,
        String companyName,
        String productName,
        Long productId,
        String type,
        String contactName,
        String contactPhone,
        String message,
        LocalDate preferredDate,
        LocalDateTime visitAt
) {
}
