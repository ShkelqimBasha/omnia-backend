package com.omnia.backend.catalog;

import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.SaleMode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Pure rules for listing attributes and for how a listing can be bought. */
public final class ListingRules {

    private ListingRules() {
    }

    /** Validated attributes plus the brand to store in the product's own column. */
    public record NormalizedListing(Map<String, Object> attributes, String brand) {
    }

    /**
     * Validates and normalizes the attributes of a listing in a category of
     * the given kind. Unknown keys, wrong types, values out of range and
     * missing required fields are rejected with {@link IllegalArgumentException}.
     *
     * <p>A "brand" sent as an attribute or as the product brand ends up in
     * {@code brand}. For cars, the make is also used as brand so that the
     * keyword search finds it.
     */
    public static NormalizedListing normalize(CategoryKind kind, Map<String, Object> raw,
                                              String requestBrand) {
        CategoryKind k = CategoryKind.fromNullable(kind);
        Map<String, Object> input = raw == null ? Map.of() : raw;
        Map<String, Object> out = new LinkedHashMap<>();
        String brand = blankToNull(requestBrand);

        for (String key : input.keySet()) {
            if (CategoryField.BRAND.equals(key)) continue;
            if (CategoryFieldCatalog.field(k, key).isEmpty()) {
                throw new IllegalArgumentException("Unknown field for this category: " + key);
            }
        }

        for (CategoryField field : CategoryFieldCatalog.fieldsFor(k)) {
            Object value = input.get(field.key());
            if (field.storedAsBrand()) {
                if (brand == null && value != null) brand = blankToNull(String.valueOf(value));
                if (brand != null && brand.length() > 100) {
                    throw new IllegalArgumentException("Brand is too long");
                }
                if (field.required() && brand == null) {
                    throw new IllegalArgumentException("Required field missing: " + field.key());
                }
                continue;
            }
            Object normalized = normalizeValue(field, value);
            if (normalized == null) {
                if (field.required()) {
                    throw new IllegalArgumentException("Required field missing: " + field.key());
                }
                continue;
            }
            out.put(field.key(), normalized);
        }

        if (k == CategoryKind.CAR && brand == null) {
            brand = (String) out.get("make");
        }
        return new NormalizedListing(out.isEmpty() ? null : out, brand);
    }

    /**
     * Every problem {@link #normalize} would report, not only the first one (same messages, same rules),
     * so a CSV row can list all its mistakes at once. Empty when the listing is valid.
     */
    public static java.util.List<String> problems(CategoryKind kind, Map<String, Object> raw, String requestBrand) {
        CategoryKind k = CategoryKind.fromNullable(kind);
        Map<String, Object> input = raw == null ? Map.of() : raw;
        java.util.List<String> problems = new java.util.ArrayList<>();
        String brand = blankToNull(requestBrand);
        for (String key : input.keySet()) {
            if (CategoryField.BRAND.equals(key)) continue;
            if (CategoryFieldCatalog.field(k, key).isEmpty()) {
                problems.add("Unknown field for this category: " + key);
            }
        }
        for (CategoryField field : CategoryFieldCatalog.fieldsFor(k)) {
            Object value = input.get(field.key());
            if (field.storedAsBrand()) {
                if (brand == null && value != null) brand = blankToNull(String.valueOf(value));
                if (brand != null && brand.length() > 100) problems.add("Brand is too long");
                else if (field.required() && brand == null) problems.add("Required field missing: " + field.key());
                continue;
            }
            try {
                if (normalizeValue(field, value) == null && field.required()) {
                    problems.add("Required field missing: " + field.key());
                }
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        return problems;
    }

    private static Object normalizeValue(CategoryField field, Object value) {
        if (value == null) return null;
        if (value instanceof Map || value instanceof Iterable) {
            throw new IllegalArgumentException("Invalid value for " + field.key());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;

        return switch (field.type()) {
            case TEXT, TEXTAREA -> {
                int max = field.maxLength() == null ? 120 : field.maxLength();
                if (text.length() > max) {
                    throw new IllegalArgumentException("Value too long for " + field.key());
                }
                yield text;
            }
            case SELECT -> {
                String code = text.toUpperCase(Locale.ROOT);
                boolean known = field.options().stream().anyMatch(o -> o.value().equals(code));
                if (!known) {
                    throw new IllegalArgumentException("Invalid option for " + field.key() + ": " + text);
                }
                yield code;
            }
            case NUMBER -> {
                BigDecimal number;
                try {
                    number = new BigDecimal(text.replace(',', '.'));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Not a number for " + field.key());
                }
                if ((field.min() != null && number.compareTo(field.min()) < 0)
                        || (field.max() != null && number.compareTo(field.max()) > 0)) {
                    throw new IllegalArgumentException("Value out of range for " + field.key());
                }
                number = number.stripTrailingZeros();
                if (number.scale() <= 0) yield number.longValueExact();
                yield number;
            }
        };
    }

    /**
     * Sale mode stored on a listing. Only cars have one; the default is
     * "Vetëm kontakt". "Kontakt + blerje online" is refused while car online
     * purchase is switched off.
     */
    public static SaleMode resolveSaleMode(CategoryKind kind, String requested,
                                           boolean carOnlinePurchaseEnabled) {
        if (CategoryKind.fromNullable(kind) != CategoryKind.CAR) return null;
        if (requested == null || requested.isBlank()) return SaleMode.CONTACT_ONLY;
        SaleMode mode;
        try {
            mode = SaleMode.valueOf(requested.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid sale mode: " + requested);
        }
        if (mode == SaleMode.CONTACT_AND_BUY && !carOnlinePurchaseEnabled) {
            throw new IllegalArgumentException("Online purchase of cars is not available yet");
        }
        return mode;
    }

    /** Whether the listing can go into the cart and be paid online. */
    public static boolean isPurchasable(CategoryKind kind, SaleMode saleMode,
                                        boolean carOnlinePurchaseEnabled) {
        CategoryKind k = CategoryKind.fromNullable(kind);
        if (k.usesCart()) return true;
        return k == CategoryKind.CAR
                && carOnlinePurchaseEnabled
                && saleMode == SaleMode.CONTACT_AND_BUY;
    }

    /** Kind of a product's category; GENERAL when unknown. */
    public static CategoryKind kindOf(com.omnia.backend.entity.Product product) {
        return product == null || product.getCategory() == null
                ? CategoryKind.GENERAL
                : CategoryKind.fromNullable(product.getCategory().getKind());
    }

    /** Car purchases are one car per order: quantity 1 and nothing else in the order. */
    public static void requireCarOrderShape(CategoryKind kind, int quantity, int distinctProducts) {
        if (CategoryKind.fromNullable(kind) != CategoryKind.CAR) return;
        if (quantity != 1 || distinctProducts != 1) {
            throw new IllegalArgumentException(
                    "A car must be bought on its own, one per order");
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
