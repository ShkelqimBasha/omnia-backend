package com.omnia.backend.catalog;

import com.omnia.backend.entity.Product;

import java.math.BigDecimal;

/** The one rule for what a buyer pays for a product. */
public final class Pricing {

    private Pricing() {
    }

    /**
     * The discount price when it is above zero and below the price; otherwise the price.
     * A zero, negative or too-high discount is ignored, so it can never lower a charge
     * below what the seller listed or raise it above the price.
     */
    public static BigDecimal buyerPrice(Product product) {
        return buyerPrice(product.getPrice(), product.getDiscountPrice());
    }

    public static BigDecimal buyerPrice(BigDecimal price, BigDecimal discount) {
        if (discount != null && discount.signum() > 0 && (price == null || discount.compareTo(price) < 0)) {
            return discount;
        }
        return price;
    }

    /** The discount when it is a real one (above zero, below the price); otherwise null. */
    public static BigDecimal validDiscount(BigDecimal price, BigDecimal discount) {
        if (discount == null || discount.signum() <= 0) return null;
        if (price != null && discount.compareTo(price) >= 0) return null;
        return discount;
    }

    /**
     * The discount to store: null when it is missing, zero or not below the price.
     * Rejects a negative discount.
     */
    public static BigDecimal normalizeDiscount(BigDecimal price, BigDecimal discount) {
        if (discount != null && discount.signum() < 0) {
            throw new IllegalArgumentException("Discount price cannot be negative");
        }
        return validDiscount(price, discount);
    }
}
