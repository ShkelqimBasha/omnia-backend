package com.omnia.backend.catalog;

import java.math.BigDecimal;
import java.util.List;

/**
 * One listing field of a category. Clients render the listing form, the
 * detail page and the filters from these definitions.
 *
 * <p>{@code key = "brand"} is stored in the product's own {@code brand}
 * column, every other key in {@code products.attributes}.
 */
public record CategoryField(
        String key,
        String label,
        FieldType type,
        boolean required,
        List<Option> options,
        String unit,
        BigDecimal min,
        BigDecimal max,
        Integer maxLength,
        boolean filterable
) {
    public enum FieldType { TEXT, TEXTAREA, NUMBER, SELECT }

    public record Option(String value, String label) { }

    public static final String BRAND = "brand";

    public boolean storedAsBrand() {
        return BRAND.equals(key);
    }

    public String optionLabel(String value) {
        if (options == null) return value;
        return options.stream().filter(o -> o.value().equals(value))
                .map(Option::label).findFirst().orElse(value);
    }
}
