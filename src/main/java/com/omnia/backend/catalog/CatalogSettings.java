package com.omnia.backend.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Feature switches of the catalog.
 *
 * <p>{@code app.catalog.car-online-purchase.enabled} (default false) turns on
 * "Bli online" for cars. Keep it off until PayPal has approved Omnia as a
 * marketplace: see docs/main-categories.md.
 */
@Component
public class CatalogSettings {

    private final boolean carOnlinePurchaseEnabled;

    public CatalogSettings(
            @Value("${app.catalog.car-online-purchase.enabled:false}") boolean carOnlinePurchaseEnabled
    ) {
        this.carOnlinePurchaseEnabled = carOnlinePurchaseEnabled;
    }

    public boolean carOnlinePurchaseEnabled() {
        return carOnlinePurchaseEnabled;
    }

    /** Null-safe accessor for classes built without Spring (tests). */
    public static boolean carOnline(CatalogSettings settings) {
        return settings != null && settings.carOnlinePurchaseEnabled();
    }
}
