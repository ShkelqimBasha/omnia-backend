package com.omnia.backend.catalog;

import com.omnia.backend.enums.SellerType;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/**
 * General catalog filters of {@code GET /api/products}, read from query parameters.
 * Every filter is optional; {@code null} / {@code false} means "no filter".
 *
 * <ul>
 *   <li>{@code inStock=true}</li>
 *   <li>{@code onSale=true}</li>
 *   <li>{@code verifiedSeller=true}</li>
 *   <li>{@code sellerType=BUSINESS|PRIVATE}</li>
 *   <li>{@code minRating=1..5} (decimals allowed, e.g. 4.5)</li>
 *   <li>{@code freeShipping=true}</li>
 *   <li>{@code publishedWithinDays=1..365}</li>
 *   <li>{@code paymentOnline=true}</li>
 * </ul>
 */
public record CatalogFilters(
        boolean inStock,
        boolean onSale,
        boolean verifiedSeller,
        SellerType sellerType,
        BigDecimal minRating,
        boolean freeShipping,
        Integer publishedWithinDays,
        boolean paymentOnline
) {

    public static final CatalogFilters NONE =
            new CatalogFilters(false, false, false, null, null, false, null, false);

    public static final int MAX_PUBLISHED_WITHIN_DAYS = 365;

    private static final BigDecimal MIN_RATING = BigDecimal.ONE;
    private static final BigDecimal MAX_RATING = new BigDecimal("5");

    public static CatalogFilters fromQuery(Map<String, String> params) {
        if (params == null || params.isEmpty()) return NONE;
        return new CatalogFilters(
                flag(params, "inStock"),
                flag(params, "onSale"),
                flag(params, "verifiedSeller"),
                sellerType(params.get("sellerType")),
                minRating(params.get("minRating")),
                flag(params, "freeShipping"),
                publishedWithinDays(params.get("publishedWithinDays")),
                flag(params, "paymentOnline"));
    }

    public boolean isEmpty() {
        return equals(NONE);
    }

    private static boolean flag(Map<String, String> params, String name) {
        String value = blankToNull(params.get(name));
        if (value == null) return false;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "1" -> true;
            case "false", "0" -> false;
            default -> throw new IllegalArgumentException(
                    "Vlerë e pavlefshme për " + name + ": lejohen vetëm true ose false");
        };
    }

    private static SellerType sellerType(String raw) {
        String value = blankToNull(raw);
        if (value == null) return null;
        try {
            return SellerType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Lloji i shitësit duhet të jetë BUSINESS ose PRIVATE");
        }
    }

    private static BigDecimal minRating(String raw) {
        String value = blankToNull(raw);
        if (value == null) return null;
        BigDecimal rating;
        try {
            rating = new BigDecimal(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Vlerësimi minimal duhet të jetë numër nga 1 deri në 5");
        }
        if (rating.compareTo(MIN_RATING) < 0 || rating.compareTo(MAX_RATING) > 0) {
            throw new IllegalArgumentException("Vlerësimi minimal duhet të jetë numër nga 1 deri në 5");
        }
        return rating;
    }

    private static Integer publishedWithinDays(String raw) {
        String value = blankToNull(raw);
        if (value == null) return null;
        int days;
        try {
            days = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Numri i ditëve të publikimit duhet të jetë numër i plotë nga 1 deri në "
                            + MAX_PUBLISHED_WITHIN_DAYS);
        }
        if (days < 1 || days > MAX_PUBLISHED_WITHIN_DAYS) {
            throw new IllegalArgumentException(
                    "Numri i ditëve të publikimit duhet të jetë numër i plotë nga 1 deri në "
                            + MAX_PUBLISHED_WITHIN_DAYS);
        }
        return days;
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
