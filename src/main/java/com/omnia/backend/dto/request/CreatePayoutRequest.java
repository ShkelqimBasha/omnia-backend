package com.omnia.backend.dto.request;

import java.util.List;

/** POST /api/admin/payouts. orderIds null or empty = every eligible unpaid order of the seller. */
public record CreatePayoutRequest(
        Long organizationId,
        List<Long> orderIds,
        String reference,
        String note
) {
}
