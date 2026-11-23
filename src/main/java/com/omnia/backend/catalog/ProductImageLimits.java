package com.omnia.backend.catalog;

import com.omnia.backend.enums.CategoryKind;

/** How many photos a listing may have: cars and property need more than a T-shirt. */
public final class ProductImageLimits {

    public static final int LARGE_LISTING_MAX = 20;

    public static final int DEFAULT_MAX = 10;

    private ProductImageLimits() {
    }

    public static int maxFor(CategoryKind kind) {
        CategoryKind resolved = CategoryKind.fromNullable(kind);
        return resolved == CategoryKind.CAR || resolved == CategoryKind.PROPERTY
                ? LARGE_LISTING_MAX
                : DEFAULT_MAX;
    }
}
