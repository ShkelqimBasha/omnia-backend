package com.omnia.backend.catalog;

import com.omnia.backend.entity.Product;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.specification.ProductSpecification;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The filters of a saved search: the same query parameters as {@code GET /api/products}
 * (keyword, categoryId, brand, minPrice, maxPrice and {@code attr.*}), normalized so that
 * equal searches compare equal.
 */
public final class SavedSearchFilters {

    private static final Set<String> PLAIN_KEYS = Set.of("keyword", "categoryId", "brand", "minPrice", "maxPrice");
    private static final int MAX_KEYS = 16;
    private static final int MAX_TEXT = 120;

    private SavedSearchFilters() {
    }

    /** Validates and normalizes; throws IllegalArgumentException for anything the listing would reject. */
    public static SortedMap<String, String> normalize(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("A saved search needs at least one filter");
        }
        if (raw.size() > MAX_KEYS) {
            throw new IllegalArgumentException("Too many filters");
        }
        SortedMap<String, String> out = new TreeMap<>();
        Map<String, String> attributes = new TreeMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (value.isEmpty()) continue;
            if (value.length() > MAX_TEXT) {
                throw new IllegalArgumentException("Filter " + key + " is too long");
            }
            if (key.startsWith("attr.")) {
                attributes.put(key, value);
            } else if (PLAIN_KEYS.contains(key)) {
                out.put(key, plain(key, value));
            } else {
                throw new IllegalArgumentException("Unknown filter: " + key);
            }
        }
        // Validates keys, ranges and numbers the same way the listing does.
        for (AttributeFilter filter : AttributeFilter.fromQuery(attributes)) {
            String suffix = switch (filter.operator()) {
                case MIN -> ".min";
                case MAX -> ".max";
                default -> "";
            };
            out.put("attr." + filter.key() + suffix,
                    filter.number() != null ? filter.number().stripTrailingZeros().toPlainString()
                            : attributes.get("attr." + filter.key()));
        }
        BigDecimal min = decimal(out.get("minPrice"));
        BigDecimal max = decimal(out.get("maxPrice"));
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("Minimum price cannot be greater than maximum price");
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("A saved search needs at least one filter");
        }
        return out;
    }

    public static String hash(Map<String, String> normalized) {
        StringBuilder canonical = new StringBuilder();
        new TreeMap<>(normalized).forEach((k, v) ->
                canonical.append(k).append('=').append(v.toLowerCase(java.util.Locale.ROOT)).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** ACTIVE products matching the filters with an id above {@code afterId}. */
    public static Specification<Product> newMatches(Map<String, String> filters, long afterId) {
        Long categoryId = filters.containsKey("categoryId") ? Long.valueOf(filters.get("categoryId")) : null;
        Specification<Product> spec = ProductSpecification.filterProducts(
                filters.get("keyword"), categoryId, null, filters.get("brand"), ProductStatus.ACTIVE,
                decimal(filters.get("minPrice")), decimal(filters.get("maxPrice")));
        Map<String, String> attributes = new TreeMap<>();
        filters.forEach((k, v) -> {
            if (k.startsWith("attr.")) attributes.put(k, v);
        });
        return spec.and(ProductSpecification.hasAttributes(AttributeFilter.fromQuery(attributes)))
                .and((root, query, cb) -> cb.greaterThan(root.get("id"), afterId));
    }

    private static String plain(String key, String value) {
        switch (key) {
            case "categoryId" -> {
                try {
                    long id = Long.parseLong(value);
                    if (id <= 0) throw new NumberFormatException();
                    return Long.toString(id);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Category id must be a positive number");
                }
            }
            case "minPrice", "maxPrice" -> {
                BigDecimal number;
                try {
                    number = new BigDecimal(value.replace(',', '.'));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Filter " + key + " must be a number");
                }
                if (number.signum() < 0) {
                    throw new IllegalArgumentException("Filter " + key + " cannot be negative");
                }
                return number.stripTrailingZeros().toPlainString();
            }
            default -> {
                return value;
            }
        }
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
