package com.omnia.backend.catalog;

import com.omnia.backend.catalog.CategoryField.FieldType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A filter on a listing field, read from query parameters:
 * {@code attr.fuel=DIESEL}, {@code attr.location=tir}, {@code attr.year.min=2015},
 * {@code attr.mileageKm.max=150000}.
 *
 * <p>Only keys of {@link CategoryFieldCatalog} are accepted, so the key can be
 * placed in a JSON path safely.
 */
public record AttributeFilter(String key, Operator operator, String text, BigDecimal number) {

    public enum Operator { EQUALS, CONTAINS, MIN, MAX }

    private static final String PREFIX = "attr.";
    private static final int MAX_FILTERS = 12;

    public static List<AttributeFilter> fromQuery(Map<String, String> params) {
        List<AttributeFilter> filters = new ArrayList<>();
        if (params == null) return filters;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!name.startsWith(PREFIX) || value.isEmpty()) continue;
            String rest = name.substring(PREFIX.length());
            Operator rangeOp = null;
            if (rest.endsWith(".min")) {
                rangeOp = Operator.MIN;
                rest = rest.substring(0, rest.length() - 4);
            } else if (rest.endsWith(".max")) {
                rangeOp = Operator.MAX;
                rest = rest.substring(0, rest.length() - 4);
            }
            String key = rest;
            CategoryField field = CategoryFieldCatalog.anyField(key)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown filter: " + key));
            if (!field.filterable()) {
                throw new IllegalArgumentException("Filter not supported: " + key);
            }
            if (field.type() == FieldType.NUMBER) {
                BigDecimal number;
                try {
                    number = new BigDecimal(value.replace(',', '.'));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Filter " + name + " must be a number");
                }
                filters.add(new AttributeFilter(key, rangeOp == null ? Operator.EQUALS : rangeOp,
                        null, number));
            } else {
                if (rangeOp != null) {
                    throw new IllegalArgumentException("Filter " + name + " is not a range");
                }
                if (value.length() > 120) {
                    throw new IllegalArgumentException("Filter " + name + " is too long");
                }
                filters.add(field.type() == FieldType.SELECT
                        ? new AttributeFilter(key, Operator.EQUALS, value.toUpperCase(Locale.ROOT), null)
                        : new AttributeFilter(key, Operator.CONTAINS, value.toLowerCase(Locale.ROOT), null));
            }
            if (filters.size() > MAX_FILTERS) {
                throw new IllegalArgumentException("Too many filters");
            }
        }
        return filters;
    }

    /** JSON path of this filter's key. Keys are validated identifiers. */
    public String jsonPath() {
        return "$." + key;
    }
}
