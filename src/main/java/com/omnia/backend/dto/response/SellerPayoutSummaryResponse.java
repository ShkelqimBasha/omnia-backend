package com.omnia.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * The seller's view: what Omnia owes now, the latest payouts (newest first, at most 50) and the
 * adjustments for refunds after a payout (newest first, at most 50).
 */
public record SellerPayoutSummaryResponse(
        Balance balance,
        BigDecimal commissionPercent,
        List<SellerPayoutResponse> payouts,
        List<PayoutAdjustmentResponse> adjustments
) {
    /** payableAmount = netAmount − adjustmentAmount (open adjustments); negative = the seller owes Omnia. */
    public record Balance(
            int orderCount,
            BigDecimal grossAmount,
            BigDecimal commissionAmount,
            BigDecimal netAmount,
            int adjustmentCount,
            BigDecimal adjustmentAmount,
            BigDecimal payableAmount
    ) {
    }
}
