package com.omnia.backend.dto.response;

import com.omnia.backend.enums.SellerType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What Omnia owes one seller now: its eligible, not yet paid out orders (gross/commission/net), minus the
 * open adjustments for orders refunded after an earlier payout. payableAmount = netAmount − adjustmentAmount;
 * negative means the seller owes Omnia.
 */
public record PayoutBalanceResponse(
        Long organizationId,
        String organizationName,
        SellerType sellerType,
        int orderCount,
        BigDecimal grossAmount,
        BigDecimal commissionAmount,
        BigDecimal netAmount,
        LocalDateTime oldestOrderAt,
        int adjustmentCount,
        BigDecimal adjustmentAmount,
        BigDecimal payableAmount
) {
}
