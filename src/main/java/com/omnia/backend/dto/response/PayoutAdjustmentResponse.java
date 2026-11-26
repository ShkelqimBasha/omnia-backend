package com.omnia.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What a seller owes Omnia back for an order refunded after it was paid out. status OPEN while
 * remainingAmount &gt; 0, SETTLED after payouts deducted it or the admin recorded it as recovered/waived.
 * source: REFUND (full), PARTIAL_REFUND or REVERSAL (PayPal took the payment back).
 */
public record PayoutAdjustmentResponse(
        Long id,
        Long organizationId,
        String organizationName,
        Long orderId,
        Long payoutId,
        String source,
        BigDecimal refundedAmount,
        BigDecimal commissionPercent,
        BigDecimal amount,
        BigDecimal settledAmount,
        BigDecimal remainingAmount,
        String status,
        String reason,
        Long createdById,
        String createdByName,
        LocalDateTime createdAt,
        LocalDateTime settledAt,
        Long settledInPayoutId,
        List<Settlement> settlements
) {
    /** kind PAYOUT (deducted in payoutId), RECOVERED or WAIVED. */
    public record Settlement(
            Long id,
            String kind,
            Long payoutId,
            BigDecimal amount,
            String note,
            String createdByName,
            LocalDateTime createdAt
    ) {
    }
}
