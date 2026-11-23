package com.omnia.backend.catalog;

import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Product;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.SaleMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ListingRulesTest {

    private static List<String> keys(CategoryKind kind) {
        return CategoryFieldCatalog.fieldsFor(kind).stream().map(CategoryField::key).toList();
    }

    @Test
    void eachMainCategoryHasTheAgreedFields() {
        // Sizes are not listing fields: each size has its own stock (ProductSizes).
        assertEquals(List.of("color", "brand", "condition"), keys(CategoryKind.CLOTHING));
        assertEquals(List.of("brand", "color", "condition"), keys(CategoryKind.SHOES));
        assertEquals(List.of("brand", "productType", "skinType", "volumeMl", "ingredients"),
                keys(CategoryKind.SKINCARE));
        assertEquals(List.of("make", "model", "year", "mileageKm", "fuel", "gearbox", "location"),
                keys(CategoryKind.CAR));
        assertEquals(List.of("dealType", "propertyType", "location", "areaM2", "rooms"),
                keys(CategoryKind.PROPERTY));
        assertTrue(keys(CategoryKind.GENERAL).isEmpty());
    }

    @Test
    void labelsAreAlbanian() {
        assertEquals("Kilometrat", CategoryFieldCatalog.field(CategoryKind.CAR, "mileageKm").orElseThrow().label());
        assertEquals("Me qira", CategoryFieldCatalog.field(CategoryKind.PROPERTY, "dealType").orElseThrow()
                .optionLabel("RENT"));
        assertEquals("Lloji i lëkurës", CategoryFieldCatalog.field(CategoryKind.SKINCARE, "skinType")
                .orElseThrow().label());
    }

    @Test
    void clothingNormalizesSelectsAndMovesBrandToTheBrandColumn() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("color", "  E zezë ");
        raw.put("condition", "new");
        raw.put("brand", "Zara");
        ListingRules.NormalizedListing listing = ListingRules.normalize(CategoryKind.CLOTHING, raw, null);

        assertEquals("Zara", listing.brand());
        assertEquals(Map.of("color", "E zezë", "condition", "NEW"), listing.attributes());
    }

    @Test
    void clothingFieldsAreOptionalSoOlderAppsKeepWorking() {
        ListingRules.NormalizedListing listing = ListingRules.normalize(CategoryKind.CLOTHING, null, "Nike");
        assertNull(listing.attributes());
        assertEquals("Nike", listing.brand());
    }

    @Test
    void carRequiresAllFieldsAndUsesMakeAsBrand() {
        Map<String, Object> car = new HashMap<>(Map.of(
                "make", "Volkswagen", "model", "Golf 7", "year", "2017", "mileageKm", 145000,
                "fuel", "DIESEL", "gearbox", "MANUAL", "location", "Tiranë"));
        ListingRules.NormalizedListing listing = ListingRules.normalize(CategoryKind.CAR, car, null);
        assertEquals("Volkswagen", listing.brand());
        assertEquals(2017L, listing.attributes().get("year"));
        assertEquals(145000L, listing.attributes().get("mileageKm"));

        car.remove("gearbox");
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> ListingRules.normalize(CategoryKind.CAR, car, null));
        assertTrue(missing.getMessage().contains("gearbox"));
    }

    @Test
    void rejectsUnknownKeysBadOptionsAndOutOfRangeNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> ListingRules.normalize(CategoryKind.SHOES, Map.of("engine", "V8"), null));
        assertThrows(IllegalArgumentException.class,
                () -> ListingRules.normalize(CategoryKind.SHOES, Map.of("condition", "BROKEN"), null));
        assertThrows(IllegalArgumentException.class,
                () -> ListingRules.normalize(CategoryKind.GENERAL, Map.of("size", "M"), null));
        assertThrows(IllegalArgumentException.class,
                () -> ListingRules.normalize(CategoryKind.SKINCARE,
                        Map.of("ingredients", "x".repeat(1001)), null));
    }

    @Test
    void theOldSingleSizeFieldsAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> ListingRules.normalize(CategoryKind.CLOTHING, Map.of("size", "M"), null));
        assertThrows(IllegalArgumentException.class,
                () -> ListingRules.normalize(CategoryKind.SHOES, Map.of("shoeSize", "42"), null));
    }

    @Test
    void propertyNeedsDealTypeTypeLocationAndArea() {
        Map<String, Object> property = Map.of("dealType", "RENT", "propertyType", "APARTMENT",
                "location", "Durrës", "areaM2", 85, "rooms", 2);
        assertEquals("RENT", ListingRules.normalize(CategoryKind.PROPERTY, property, null)
                .attributes().get("dealType"));
        assertThrows(IllegalArgumentException.class, () -> ListingRules.normalize(CategoryKind.PROPERTY,
                Map.of("dealType", "RENT", "propertyType", "APARTMENT", "location", "Durrës"), null));
    }

    @Test
    void onlyCartCategoriesAndOptedInCarsArePurchasable() {
        for (CategoryKind kind : List.of(CategoryKind.GENERAL, CategoryKind.CLOTHING,
                CategoryKind.SHOES, CategoryKind.SKINCARE)) {
            assertTrue(ListingRules.isPurchasable(kind, null, false), kind.name());
        }
        assertFalse(ListingRules.isPurchasable(CategoryKind.PROPERTY, null, true));
        assertFalse(ListingRules.isPurchasable(CategoryKind.CAR, SaleMode.CONTACT_ONLY, true));
        assertFalse(ListingRules.isPurchasable(CategoryKind.CAR, SaleMode.CONTACT_AND_BUY, false));
        assertTrue(ListingRules.isPurchasable(CategoryKind.CAR, SaleMode.CONTACT_AND_BUY, true));
    }

    @Test
    void saleModeDefaultsToContactOnlyAndBuyOnlineNeedsTheFlag() {
        assertEquals(SaleMode.CONTACT_ONLY, ListingRules.resolveSaleMode(CategoryKind.CAR, null, false));
        assertNull(ListingRules.resolveSaleMode(CategoryKind.CLOTHING, "CONTACT_AND_BUY", true));
        assertThrows(IllegalArgumentException.class,
                () -> ListingRules.resolveSaleMode(CategoryKind.CAR, "CONTACT_AND_BUY", false));
        assertEquals(SaleMode.CONTACT_AND_BUY,
                ListingRules.resolveSaleMode(CategoryKind.CAR, "contact_and_buy", true));
        assertThrows(IllegalArgumentException.class,
                () -> ListingRules.resolveSaleMode(CategoryKind.CAR, "DEPOSIT", true));
    }

    @Test
    void carIsBoughtAloneOnePerOrder() {
        assertDoesNotThrow(() -> ListingRules.requireCarOrderShape(CategoryKind.CAR, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> ListingRules.requireCarOrderShape(CategoryKind.CAR, 2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> ListingRules.requireCarOrderShape(CategoryKind.CAR, 1, 2));
        assertDoesNotThrow(() -> ListingRules.requireCarOrderShape(CategoryKind.CLOTHING, 3, 4));
    }

    @Test
    void purchaseGuardBlocksPropertyAndContactOnlyCars() {
        Product property = Product.builder()
                .category(Category.builder().kind(CategoryKind.PROPERTY).build()).build();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PurchaseGuard.requirePurchasable(property, true));
        assertTrue(e.getMessage().contains("visit"));

        Product car = Product.builder().saleMode(SaleMode.CONTACT_ONLY)
                .category(Category.builder().kind(CategoryKind.CAR).build()).build();
        assertThrows(IllegalArgumentException.class, () -> PurchaseGuard.requirePurchasable(car, true));

        Product shirt = Product.builder()
                .category(Category.builder().kind(CategoryKind.CLOTHING).build()).build();
        assertDoesNotThrow(() -> PurchaseGuard.requirePurchasable(shirt, false));
    }

    @Test
    void attributeFiltersAcceptOnlyKnownKeys() {
        List<AttributeFilter> filters = AttributeFilter.fromQuery(Map.of(
                "attr.fuel", "diesel", "attr.year.min", "2015", "attr.location", "TIR",
                "page", "0", "attr.rooms", ""));
        assertEquals(3, filters.size());
        assertTrue(filters.contains(new AttributeFilter("fuel", AttributeFilter.Operator.EQUALS, "DIESEL", null)));
        assertTrue(filters.contains(new AttributeFilter("year", AttributeFilter.Operator.MIN, null,
                new BigDecimal("2015"))));
        assertTrue(filters.contains(new AttributeFilter("location", AttributeFilter.Operator.CONTAINS, "tir", null)));

        assertThrows(IllegalArgumentException.class,
                () -> AttributeFilter.fromQuery(Map.of("attr.x') OR 1=1 --", "1")));
        assertThrows(IllegalArgumentException.class,
                () -> AttributeFilter.fromQuery(Map.of("attr.brand", "Nike")));
        assertThrows(IllegalArgumentException.class,
                () -> AttributeFilter.fromQuery(Map.of("attr.fuel.min", "1")));
        assertThrows(IllegalArgumentException.class,
                () -> AttributeFilter.fromQuery(Map.of("attr.year.min", "abc")));
    }
}
