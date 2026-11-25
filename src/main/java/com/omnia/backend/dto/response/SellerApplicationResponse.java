package com.omnia.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/** A seller application, for the applicant (GET /me) and the platform admin list. */
public record SellerApplicationResponse(
        Long id,
        String sellerType,
        String storeName,
        String phone,
        String city,
        String nipt,
        List<Long> categoryIds,
        List<String> categoryNames,
        String description,
        String status,
        String rejectReason,
        Long organizationId,
        OffsetDateTime createdAt,
        OffsetDateTime reviewedAt,
        Applicant applicant
) {
    public record Applicant(Long id, String name, String email) {
    }
}
