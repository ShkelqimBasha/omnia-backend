package com.omnia.backend.catalog;

import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductVariant;
import com.omnia.backend.enums.CategoryKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.omnia.backend.catalog.ProductSizesTest.product;
import static com.omnia.backend.catalog.ProductSizesTest.size;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SizeStockTest {

    private Product tshirt() {
        Product p = product(CategoryKind.CLOTHING);
        p.getVariants().addAll(List.of(size(p, 1L, "M", 3), size(p, 2L, "L", 0)));
        p.setStock(3);
        return p;
    }

    @Test
    void aSizedProductNeedsOneOfItsOwnSizes() {
        Product p = tshirt();
        assertThrows(IllegalArgumentException.class, () -> SizeStock.chosenSize(p, null));
        assertThrows(IllegalArgumentException.class, () -> SizeStock.chosenSize(p, 99L));
        assertEquals("M", SizeStock.chosenSize(p, 1L).getVariantValue());
    }

    @Test
    void aProductWithoutSizesRefusesASize() {
        Product p = product(CategoryKind.GENERAL);
        assertNull(SizeStock.chosenSize(p, null));
        assertThrows(IllegalArgumentException.class, () -> SizeStock.chosenSize(p, 1L));
    }

    @Test
    void reserveTakesStockFromTheSizeAndKeepsTheSum() {
        Product p = tshirt();
        ProductVariant m = SizeStock.reserve(p, 1L, 2);
        assertSame(p.getVariants().get(0), m);
        assertEquals(1, m.getStock());
        assertEquals(1, p.getStock());
    }

    @Test
    void reserveRefusesSoldOutAndTooMuch() {
        Product p = tshirt();
        IllegalArgumentException soldOut = assertThrows(IllegalArgumentException.class,
                () -> SizeStock.reserve(p, 2L, 1));
        assertTrue(soldOut.getMessage().contains("ka mbaruar"), soldOut.getMessage());
        IllegalArgumentException tooMuch = assertThrows(IllegalArgumentException.class,
                () -> SizeStock.reserve(p, 1L, 4));
        assertTrue(tooMuch.getMessage().contains("mbeten 3"), tooMuch.getMessage());
        assertEquals(3, p.getVariants().get(0).getStock(), "nothing taken on refusal");
        assertEquals(3, p.getStock());
    }

    @Test
    void unsizedProductsWorkAsBefore() {
        Product p = product(CategoryKind.GENERAL);
        p.setStock(5);
        assertNull(SizeStock.reserve(p, null, 5));
        assertEquals(0, p.getStock());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SizeStock.reserve(p, null, 1));
        assertEquals("Insufficient stock for product: Produkt", e.getMessage());
        assertTrue(SizeStock.restore(p, null, 2));
        assertEquals(2, p.getStock());
    }

    @Test
    void restorePutsStockBackOnTheLineSize() {
        Product p = tshirt();
        assertTrue(SizeStock.restore(p, 2L, 4));
        assertEquals(4, p.getVariants().get(1).getStock());
        assertEquals(7, p.getStock());
    }

    @Test
    void restoreOfADeletedSizeUsesTheOnlySizeOrNothing() {
        Product single = product(CategoryKind.SHOES);
        single.getVariants().add(size(single, 7L, "42", 1));
        assertTrue(SizeStock.restore(single, null, 2), "orders from before sizes existed");
        assertEquals(3, single.getVariants().get(0).getStock());
        assertEquals(3, single.getStock());

        Product several = tshirt();
        assertFalse(SizeStock.restore(several, null, 2));
        assertFalse(SizeStock.restore(several, 99L, 2));
        assertEquals(3, several.getStock());
    }

    @Test
    void availableIsTheSizeStockOrTheProductStock() {
        Product p = tshirt();
        assertEquals(0, SizeStock.available(p, p.getVariants().get(1)));
        assertEquals(3, SizeStock.available(p, null));
    }
}
