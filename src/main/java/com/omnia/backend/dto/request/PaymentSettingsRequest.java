package com.omnia.backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** A company's accepted payment methods; the COD limits are optional (null = no limit). */
public record PaymentSettingsRequest(
        @NotNull Boolean acceptsCashOnDelivery,
        @NotNull Boolean acceptsPaypal,
        @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 7, fraction = 2)
        BigDecimal codMinOrderAmount,
        @DecimalMin(value = "0.00", inclusive = false) @DecimalMax("1000000.00") @Digits(integer = 7, fraction = 2)
        BigDecimal codMaxOrderAmount
) {
}
