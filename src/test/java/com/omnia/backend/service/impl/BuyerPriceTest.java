package com.omnia.backend.service.impl;

import com.omnia.backend.catalog.SavedSearchFilters;
import com.omnia.backend.entity.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuyerPriceTest {

    private static Product product(String price, String discount) {
        return Product.builder().price(new BigDecimal(price))
                .discountPrice(discount == null ? null : new BigDecimal(discount)).build();
    }

    @Test
    void buyerPriceIsTheLowerValidDiscount() {
        assertEquals(new BigDecimal("100"), ProductServiceImpl.buyerPrice(product("100", null)));
        assertEquals(new BigDecimal("80"), ProductServiceImpl.buyerPrice(product("100", "80")));
        assertEquals(new BigDecimal("100"), ProductServiceImpl.buyerPrice(product("100", "120")));
        assertEquals(new BigDecimal("100"), ProductServiceImpl.buyerPrice(product("100", "0")));
    }

    @Test
    void savedSearchFiltersAreNormalizedAndHashedStably() {
        var a = SavedSearchFilters.normalize(Map.of("maxPrice", "8000,00", "attr.year.min", "2010.0", "keyword", " Golf "));
        var b = SavedSearchFilters.normalize(Map.of("keyword", "golf", "attr.year.min", "2010", "maxPrice", "8000"));
        assertEquals("8000", a.get("maxPrice"));
        assertEquals("2010", a.get("attr.year.min"));
        assertEquals(SavedSearchFilters.hash(a), SavedSearchFilters.hash(b));
        assertThrows(IllegalArgumentException.class, () -> SavedSearchFilters.normalize(Map.of("attr.location.min", "1")));
        assertThrows(IllegalArgumentException.class, () -> SavedSearchFilters.normalize(Map.of("categoryId", "-3")));
        assertThrows(IllegalArgumentException.class, () -> SavedSearchFilters.normalize(Map.of()));
    }
}
