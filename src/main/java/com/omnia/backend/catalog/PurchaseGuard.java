package com.omnia.backend.catalog;

import com.omnia.backend.entity.Product;
import com.omnia.backend.enums.CategoryKind;

/** Server-side gate for the cart and checkout: clients can hide buttons, this enforces it. */
public final class PurchaseGuard {

    private PurchaseGuard() {
    }

    public static void requirePurchasable(Product product, boolean carOnlinePurchaseEnabled) {
        CategoryKind kind = ListingRules.kindOf(product);
        if (!ListingRules.isPurchasable(kind, product.getSaleMode(), carOnlinePurchaseEnabled)) {
            throw new IllegalArgumentException(kind == CategoryKind.PROPERTY
                    ? "Properties cannot be bought online. Contact the seller or request a visit."
                    : "This listing cannot be bought online. Contact the seller.");
        }
    }
}
