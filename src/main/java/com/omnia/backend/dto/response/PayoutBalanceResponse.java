package com.omnia.backend.dto.response;

import com.omnia.backend.enums.SellerType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** What Omnia owes one seller now: its eligible, not yet paid out orders. */
public record PayoutBalanceResponse(
        Long organizationId,
        String organizationName,
        SellerType sellerType,
        int orderCount,
        BigDecimal grossAmount,
        BigDecimal commissionAmount,
        BigDecimal netAmount,
        LocalDateTime oldestOrderAt
) {
}
