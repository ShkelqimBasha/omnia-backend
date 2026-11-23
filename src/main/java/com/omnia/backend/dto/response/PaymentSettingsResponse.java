package com.omnia.backend.dto.response;

import com.omnia.backend.entity.Organization;

import java.math.BigDecimal;

public record PaymentSettingsResponse(
        Long organizationId,
        boolean acceptsCashOnDelivery,
        boolean acceptsPaypal,
        BigDecimal codMinOrderAmount,
        BigDecimal codMaxOrderAmount
) {
    public static PaymentSettingsResponse of(Organization organization) {
        return new PaymentSettingsResponse(organization.getId(), organization.isAcceptsCashOnDelivery(),
                organization.isAcceptsPaypal(), organization.getCodMinOrderAmount(),
                organization.getCodMaxOrderAmount());
    }
}
