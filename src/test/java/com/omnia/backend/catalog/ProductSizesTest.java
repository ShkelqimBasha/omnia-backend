package com.omnia.backend.catalog;

import com.omnia.backend.catalog.ProductSizes.SizeInput;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductVariant;
import com.omnia.backend.enums.CategoryKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductSizesTest {

    @Test
    void onlyClothingAndShoesHaveSizes() {
        assertTrue(ProductSizes.supportsSizes(CategoryKind.CLOTHING));
        assertTrue(ProductSizes.supportsSizes(CategoryKind.SHOES));
        for (CategoryKind kind : List.of(CategoryKind.GENERAL, CategoryKind.SKINCARE,
                CategoryKind.CAR, CategoryKind.PROPERTY)) {
            assertFalse(ProductSizes.supportsSizes(kind));
            assertThrows(IllegalArgumentException.class, () -> ProductSizes.normalize(kind,
                    List.of(new SizeInput("M", 1))));
        }
        assertFalse(ProductSizes.supportsSizes(null));
    }

    @Test
    void clothingValuesAreTheAgreedCodes() {
        assertEquals("M", ProductSizes.normalizeValue(CategoryKind.CLOTHING, " m "));
        assertEquals("XXXL", ProductSizes.normalizeValue(CategoryKind.CLOTHING, "3xl"));
        assertEquals("ONE_SIZE", ProductSizes.normalizeValue(CategoryKind.CLOTHING, "one size"));
        assertThrows(IllegalArgumentException.class,
                () -> ProductSizes.normalizeValue(CategoryKind.CLOTHING, "XXXXL"));
        assertThrows(IllegalArgumentException.class,
                () -> ProductSizes.normalizeValue(CategoryKind.CLOTHING, "42"));
        assertThrows(IllegalArgumentException.class,
                () -> ProductSizes.normalizeValue(CategoryKind.CLOTHING, " "));
    }

    @Test
    void shoeValuesAreHalfSizesFrom15To50() {
        assertEquals("38", ProductSizes.normalizeValue(CategoryKind.SHOES, "38"));
        assertEquals("42", ProductSizes.normalizeValue(CategoryKind.SHOES, "42.0"));
        assertEquals("42.5", ProductSizes.normalizeValue(CategoryKind.SHOES, "42,5"));
        assertEquals("15", ProductSizes.normalizeValue(CategoryKind.SHOES, "15"));
        assertEquals("50", ProductSizes.normalizeValue(CategoryKind.SHOES, "50"));
        for (String bad : List.of("14.5", "50.5", "42.3", "M", "", "-40")) {
            assertThrows(IllegalArgumentException.class,
                    () -> ProductSizes.normalizeValue(CategoryKind.SHOES, bad), bad);
        }
    }

    @Test
    void labelsAndOrderLineText() {
        assertEquals("3XL", ProductSizes.label("XXXL"));
        assertEquals("Një masë", ProductSizes.label("ONE_SIZE"));
        assertEquals("M", ProductSizes.label("M"));
        assertEquals("42.5", ProductSizes.label("42.5"));
        assertEquals("Masa: 3XL", ProductSizes.orderInfo(CategoryKind.CLOTHING, "XXXL"));
        assertEquals("Numri: 42.5", ProductSizes.orderInfo(CategoryKind.SHOES, "42.5"));
    }

    @Test
    void normalizeSortsAndRefusesDuplicatesAndBadStock() {
        List<SizeInput> sizes = ProductSizes.normalize(CategoryKind.CLOTHING, List.of(
                new SizeInput("xl", 1), new SizeInput("S", 0), new SizeInput("ONE_SIZE", 2),
                new SizeInput("m", 5)));
        assertEquals(List.of("S", "M", "XL", "ONE_SIZE"), sizes.stream().map(SizeInput::value).toList());

        List<SizeInput> shoes = ProductSizes.normalize(CategoryKind.SHOES, List.of(
                new SizeInput("42.5", 1), new SizeInput("40", 1),
                new SizeInput("38", 1)));
        assertEquals(List.of("38", "40", "42.5"), shoes.stream().map(SizeInput::value).toList());

        assertThrows(IllegalArgumentException.class, () -> ProductSizes.normalize(CategoryKind.CLOTHING,
                List.of(new SizeInput("M", 1), new SizeInput("m", 2))));
        assertThrows(IllegalArgumentException.class, () -> ProductSizes.normalize(CategoryKind.SHOES,
                List.of(new SizeInput("42", 1), new SizeInput("42.0", 2))));
        assertThrows(IllegalArgumentException.class, () -> ProductSizes.normalize(CategoryKind.CLOTHING,
                List.of(new SizeInput("M", -1))));
        assertThrows(IllegalArgumentException.class, () -> ProductSizes.normalize(CategoryKind.CLOTHING,
                List.of(new SizeInput("M", ProductSizes.MAX_STOCK + 1))));
        assertThrows(IllegalArgumentException.class, () -> ProductSizes.normalize(CategoryKind.CLOTHING,
                List.of(new SizeInput("M", null))));

        List<SizeInput> tooMany = new ArrayList<>();
        for (int i = 0; i <= ProductSizes.MAX_SIZES; i++) {
            tooMany.add(new SizeInput(String.valueOf(15 + i * 0.5), 1));
        }
        assertThrows(IllegalArgumentException.class,
                () -> ProductSizes.normalize(CategoryKind.SHOES, tooMany));

        assertTrue(ProductSizes.normalize(CategoryKind.GENERAL, List.of()).isEmpty(),
                "an empty list is fine for any kind");
        assertTrue(ProductSizes.normalize(CategoryKind.GENERAL, null).isEmpty());
    }

    @Test
    void sizesOfAProductIgnoreOtherVariantsAndOtherKinds() {
        Product shoe = product(CategoryKind.SHOES);
        shoe.getVariants().addAll(List.of(
                size(shoe, 1L, "42.5", 1), size(shoe, 2L, "38", 1), size(shoe, 3L, "40", 1),
                ProductVariant.builder().id(4L).product(shoe).variantName("Color")
                        .variantValue("Red").stock(9).build()));
        assertEquals(List.of("38", "40", "42.5"), ProductSizes.sizesOf(shoe).stream()
                .map(ProductVariant::getVariantValue).toList());

        Product clothing = product(CategoryKind.CLOTHING);
        List<ProductVariant> shuffled = new ArrayList<>(List.of(size(clothing, 1L, "ONE_SIZE", 1),
                size(clothing, 2L, "XXXL", 1), size(clothing, 3L, "XS", 1), size(clothing, 4L, "L", 1)));
        Collections.reverse(shuffled);
        clothing.getVariants().addAll(shuffled);
        assertEquals(List.of("XS", "L", "XXXL", "ONE_SIZE"), ProductSizes.sizesOf(clothing).stream()
                .map(ProductVariant::getVariantValue).toList());

        Product phone = product(CategoryKind.GENERAL);
        phone.getVariants().add(size(phone, 5L, "M", 3));
        assertTrue(ProductSizes.sizesOf(phone).isEmpty(), "sizes only count for clothing and shoes");
    }

    static Product product(CategoryKind kind) {
        return Product.builder().id(10L).name("Produkt")
                .category(Category.builder().id(1L).name(kind.name()).kind(kind).build())
                .stock(0).build();
    }

    static ProductVariant size(Product product, Long id, String value, int stock) {
        return ProductVariant.builder().id(id).product(product).variantName(ProductSizes.VARIANT_NAME)
                .variantValue(value).stock(stock).build();
    }
}
