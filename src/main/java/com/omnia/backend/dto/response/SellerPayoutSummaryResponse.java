package com.omnia.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** The seller's view: what Omnia owes now and the latest payouts (newest first, at most 50). */
public record SellerPayoutSummaryResponse(
        Balance balance,
        BigDecimal commissionPercent,
        List<SellerPayoutResponse> payouts
) {
    public record Balance(
            int orderCount,
            BigDecimal grossAmount,
            BigDecimal commissionAmount,
            BigDecimal netAmount
    ) {
    }
}
