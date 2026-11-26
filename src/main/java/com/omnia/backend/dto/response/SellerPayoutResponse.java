package com.omnia.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A recorded transfer to a seller. netAmount = gross − commission of its orders; deductionAmount = open
 * adjustments (refunds after earlier payouts) settled by it; transferAmount = netAmount − deductionAmount,
 * what was actually sent.
 */
public record SellerPayoutResponse(
        Long id,
        Long organizationId,
        String organizationName,
        BigDecimal grossAmount,
        BigDecimal commissionPercent,
        BigDecimal commissionAmount,
        BigDecimal netAmount,
        BigDecimal deductionAmount,
        BigDecimal transferAmount,
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
