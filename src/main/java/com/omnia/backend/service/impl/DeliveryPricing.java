package com.omnia.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Allocate one checkout-level surcharge without losing or duplicating cents. */
public final class DeliveryPricing {
    public static final BigDecimal DEFAULT_EXPRESS_SURCHARGE = new BigDecimal("5.00");
    /** Standard delivery is free when one seller's subtotal is above this amount (strictly greater). */
    public static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("50.00");
    /** Standard delivery fee per seller below {@link #FREE_SHIPPING_THRESHOLD}. */
    public static final BigDecimal STANDARD_SHIPPING_FEE = new BigDecimal("3.50");
    private DeliveryPricing() {}
    public static BigDecimal validateSurcharge(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.compareTo(new BigDecimal("10000.00")) > 0)
            throw new IllegalArgumentException("Invalid express delivery configuration");
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
    public static List<BigDecimal> allocate(BigDecimal surcharge, int orderCount) {
        if (orderCount <= 0) throw new IllegalArgumentException("No orders to allocate delivery fee");
        long cents = validateSurcharge(surcharge).movePointRight(2).longValueExact();
        List<BigDecimal> amounts = new ArrayList<>();
        for (int index = 0; index < orderCount; index++)
            amounts.add(BigDecimal.valueOf(cents / orderCount + (index < cents % orderCount ? 1 : 0), 2));
        return amounts;
    }
}
