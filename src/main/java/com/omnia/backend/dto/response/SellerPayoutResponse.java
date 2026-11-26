package com.omnia.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** A recorded transfer to a seller. */
public record SellerPayoutResponse(
        Long id,
        Long organizationId,
        String organizationName,
        BigDecimal grossAmount,
        BigDecimal commissionPercent,
        BigDecimal commissionAmount,
        BigDecimal netAmount,
        int orderCount,
        List<Long> orderIds,
        String reference,
        String note,
        LocalDateTime paidAt,
        Long createdById,
        String createdByName,
        LocalDateTime createdAt
) {
}
