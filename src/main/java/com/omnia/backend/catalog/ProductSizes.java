package com.omnia.backend.catalog;

import com.omnia.backend.entity.ProductVariant;
import com.omnia.backend.enums.CategoryKind;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sizes of clothing and shoe listings. Each size is a {@code product_variants}
 * row named {@link #VARIANT_NAME} with its own stock; the product's stock is
 * the sum of its sizes.
 */
public final class ProductSizes {

    public static final String VARIANT_NAME = "SIZE";

    public static final int MAX_SIZES = 40;

    public static final int MAX_STOCK = 1_000_000;

    private static final BigDecimal SHOE_MIN = new BigDecimal("15");
    private static final BigDecimal SHOE_MAX = new BigDecimal("50");
    private static final BigDecimal TWO = new BigDecimal("2");

    /** Clothing sizes in display order, value to label. */
    private static final Map<String, String> CLOTHING = new LinkedHashMap<>();

    static {
        CLOTHING.put("XS", "XS");
        CLOTHING.put("S", "S");
        CLOTHING.put("M", "M");
        CLOTHING.put("L", "L");
        CLOTHING.put("XL", "XL");
        CLOTHING.put("XXL", "XXL");
        CLOTHING.put("XXXL", "3XL");
        CLOTHING.put("ONE_SIZE", "Një masë");
    }

    private ProductSizes() {
    }

    /** A requested size: value and stock. */
    public record SizeInput(String value, Integer stock) {
    }

    public static boolean supportsSizes(CategoryKind kind) {
        CategoryKind k = CategoryKind.fromNullable(kind);
        return k == CategoryKind.CLOTHING || k == CategoryKind.SHOES;
    }

    public static boolean isSize(ProductVariant variant) {
        return variant != null && VARIANT_NAME.equals(variant.getVariantName());
    }

    /**
     * The sizes a buyer chooses from: the SIZE variants of a clothing or shoe
     * product in display order; empty for every other product.
     */
    public static List<ProductVariant> sizesOf(com.omnia.backend.entity.Product product) {
        if (product == null) return List.of();
        CategoryKind kind = ListingRules.kindOf(product);
        return supportsSizes(kind) ? sizesOf(kind, product.getVariants()) : List.of();
    }

    /** Sizes of a product's variants, in display order for the kind. */
    public static List<ProductVariant> sizesOf(CategoryKind kind, List<ProductVariant> variants) {
        if (variants == null) return List.of();
        return variants.stream().filter(ProductSizes::isSize)
                .sorted(order(kind))
                .toList();
    }

    public static Comparator<ProductVariant> order(CategoryKind kind) {
        return Comparator.comparing(v -> sortKey(kind, v.getVariantValue()));
    }

    /**
     * The canonical stored value for a size of this kind, or an
     * {@link IllegalArgumentException} when the value is not allowed.
     * Clothing: one of XS…XXXL, ONE_SIZE (case-insensitive; "3XL" is accepted for XXXL).
     * Shoes: 15–50 in steps of 0.5, written "38" or "42.5" (a comma is accepted).
     */
    public static String normalizeValue(CategoryKind kind, String raw) {
        CategoryKind k = CategoryKind.fromNullable(kind);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Masa nuk mund të jetë bosh");
        }
        String text = raw.trim();
        if (k == CategoryKind.CLOTHING) {
            String code = text.toUpperCase(Locale.ROOT).replace(' ', '_');
            if ("3XL".equals(code)) code = "XXXL";
            if (!CLOTHING.containsKey(code)) {
                throw new IllegalArgumentException("Masë e pavlefshme për veshje: " + text);
            }
            return code;
        }
        if (k == CategoryKind.SHOES) {
            BigDecimal number;
            try {
                number = new BigDecimal(text.replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Numër i pavlefshëm për këpucë: " + text);
            }
            boolean halfStep = number.multiply(TWO).stripTrailingZeros().scale() <= 0;
            if (number.compareTo(SHOE_MIN) < 0 || number.compareTo(SHOE_MAX) > 0 || !halfStep) {
                throw new IllegalArgumentException("Numër i pavlefshëm për këpucë: " + text);
            }
            return number.stripTrailingZeros().toPlainString();
        }
        throw new IllegalArgumentException("Kjo kategori nuk ka masa");
    }

    /** Most sizes a catalog search may ask for at once. */
    public static final int MAX_FILTER_SIZES = 20;

    /**
     * Sizes for a catalog search, from {@code sizes=M,L} or {@code sizes=42,42.5}:
     * trimmed, upper-case clothing codes ("3XL" becomes XXXL), shoe numbers
     * without trailing zeros. Anything that cannot be a size is dropped.
     */
    public static java.util.List<String> parseFilter(String raw) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split("[,;]")) {
            String text = part.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            if (text.isEmpty() || text.length() > 10) continue;
            if ("3XL".equals(text)) text = "XXXL";
            String value;
            if (CLOTHING.containsKey(text)) {
                value = text;
            } else {
                try {
                    BigDecimal number = new BigDecimal(text.replace(',', '.'));
                    if (number.compareTo(SHOE_MIN) < 0 || number.compareTo(SHOE_MAX) > 0) continue;
                    value = number.stripTrailingZeros().toPlainString();
                } catch (NumberFormatException e) {
                    continue;
                }
            }
            if (!out.contains(value)) out.add(value);
            if (out.size() == MAX_FILTER_SIZES) break;
        }
        return out;
    }

    /** Display label: "3XL" for XXXL, "Një masë" for ONE_SIZE, the number for shoes. */
    public static String label(String value) {
        if (value == null) return null;
        return CLOTHING.getOrDefault(value, value);
    }

    /** Snapshot stored on the order line: "Masa: M" (clothing) or "Numri: 42.5" (shoes). */
    public static String orderInfo(CategoryKind kind, String value) {
        if (value == null) return null;
        String prefix = CategoryKind.fromNullable(kind) == CategoryKind.SHOES
                ? "Numri: " : "Masa: ";
        return prefix + label(value);
    }

    /**
     * Validates a requested list of sizes for a product of this kind and
     * returns it with canonical values, in display order.
     */
    public static List<SizeInput> normalize(CategoryKind kind, List<SizeInput> sizes) {
        if (sizes == null || sizes.isEmpty()) return List.of();
        if (!supportsSizes(kind)) {
            throw new IllegalArgumentException("Masat lejohen vetëm për veshje dhe këpucë");
        }
        if (sizes.size() > MAX_SIZES) {
            throw new IllegalArgumentException("Të shumtën " + MAX_SIZES + " masa për produkt");
        }
        Set<String> seen = new HashSet<>();
        return sizes.stream().map(size -> {
            if (size == null) throw new IllegalArgumentException("Masa nuk mund të jetë bosh");
            String value = normalizeValue(kind, size.value());
            if (!seen.add(value)) {
                throw new IllegalArgumentException("Masa përsëritet: " + label(value));
            }
            Integer stock = size.stock();
            if (stock == null || stock < 0 || stock > MAX_STOCK) {
                throw new IllegalArgumentException(
                        "Sasia për masën " + label(value) + " duhet të jetë 0–" + MAX_STOCK);
            }
            return new SizeInput(value, stock);
        }).sorted(Comparator.comparing(s -> sortKey(kind, s.value()))).toList();
    }

    public static int totalStock(List<ProductVariant> sizes) {
        return sizes.stream().mapToInt(v -> v.getStock() == null ? 0 : v.getStock()).sum();
    }

    private static String sortKey(CategoryKind kind, String value) {
        if (value == null) return "";
        int index = 0;
        for (String code : CLOTHING.keySet()) {
            if (code.equals(value)) return String.format("A%03d", index);
            index++;
        }
        try {
            // Shoe sizes: numeric order, as a fixed-width key (max 50.5 -> "B0505").
            BigDecimal number = new BigDecimal(value);
            return String.format("B%04d", number.multiply(BigDecimal.TEN).intValue());
        } catch (NumberFormatException e) {
            return "C" + value;
        }
    }
}
