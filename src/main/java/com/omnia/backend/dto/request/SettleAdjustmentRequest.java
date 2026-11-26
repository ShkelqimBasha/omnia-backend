package com.omnia.backend.dto.request;

/**
 * POST /api/admin/payouts/adjustments/{id}/settle. kind RECOVERED (the seller paid it back outside a payout,
 * default) or WAIVED (Omnia does not ask for it, e.g. a wrong split); note is required.
 */
public record SettleAdjustmentRequest(
        String kind,
        String note
) {
}
