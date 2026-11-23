package com.omnia.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payment methods a cart may use: only those every company in it accepts. {@code reason}
 * says (in Albanian) why a method is unavailable; {@code message} explains mixed rules.
 */
public record PaymentOptionsResponse(
        Method cashOnDelivery,
        Method paypal,
        boolean mixedRules,
        String message,
        List<Company> companies
) {
    public record Method(boolean available, String reason) {
    }

    public record Company(
            Long organizationId,
            String organizationName,
            BigDecimal subtotal,
            boolean acceptsCashOnDelivery,
            boolean acceptsPaypal,
            BigDecimal codMinOrderAmount,
            BigDecimal codMaxOrderAmount,
            boolean cashOnDeliveryAvailable,
            boolean paypalAvailable
    ) {
    }
}
