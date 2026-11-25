package com.omnia.backend.catalog;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Catalog sorts computed from data outside the product row ({@code sortBy} values).
 * They always put the "best" listing first; {@code sortDir} is not used for them.
 */
public enum CatalogSort {
    /** Average review rating, then number of reviews; listings without reviews last. */
    RATING("rating"),
    /** Views in the last 30 days (product_daily_stats). */
    POPULAR("popular"),
    /** Units sold in delivered or paid orders. */
    BESTSELLING("bestselling"),
    /** With a keyword: name matches first, then brand/description; otherwise newest first. */
    RELEVANCE("relevance");

    private final String key;

    CatalogSort(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<CatalogSort> fromKey(String sortBy) {
        if (sortBy == null) return Optional.empty();
        String normalized = sortBy.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(sort -> sort.key.equals(normalized)).findFirst();
    }
}
