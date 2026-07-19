package com.omnia.backend.specification;

import com.omnia.backend.entity.Product;
import com.omnia.backend.enums.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public final class ProductSpecification {

    private static final char LIKE_ESCAPE_CHARACTER = '\\';

    private ProductSpecification() {
    }

    public static Specification<Product> filterProducts(
            String keyword,
            Long categoryId,
            String brand,
            ProductStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        return Specification.allOf(
                List.of(
                        containsKeyword(keyword),
                        hasCategory(categoryId),
                        hasBrand(brand),
                        hasStatus(status),
                        priceGreaterThanOrEqual(minPrice),
                        priceLessThanOrEqual(maxPrice)
                )
        );
    }

    private static Specification<Product> containsKeyword(
            String keyword
    ) {
        return (root, query, criteriaBuilder) -> {
            if (keyword == null || keyword.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            String normalizedKeyword = keyword
                    .trim()
                    .toLowerCase(Locale.ROOT);

            String likePattern = "%"
                    + escapeLikePattern(normalizedKeyword)
                    + "%";

            return criteriaBuilder.or(
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("name")),
                            likePattern,
                            LIKE_ESCAPE_CHARACTER
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("description")),
                            likePattern,
                            LIKE_ESCAPE_CHARACTER
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("brand")),
                            likePattern,
                            LIKE_ESCAPE_CHARACTER
                    )
            );
        };
    }

    private static Specification<Product> hasCategory(
            Long categoryId
    ) {
        return (root, query, criteriaBuilder) -> {
            if (categoryId == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("category").get("id"),
                    categoryId
            );
        };
    }

    private static Specification<Product> hasBrand(
            String brand
    ) {
        return (root, query, criteriaBuilder) -> {
            if (brand == null || brand.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            String normalizedBrand = brand
                    .trim()
                    .toLowerCase(Locale.ROOT);

            return criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get("brand")),
                    normalizedBrand
            );
        };
    }

    private static Specification<Product> hasStatus(
            ProductStatus status
    ) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("status"),
                    status
            );
        };
    }

    private static Specification<Product> priceGreaterThanOrEqual(
            BigDecimal minPrice
    ) {
        return (root, query, criteriaBuilder) -> {
            if (minPrice == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.greaterThanOrEqualTo(
                    root.get("price"),
                    minPrice
            );
        };
    }

    private static Specification<Product> priceLessThanOrEqual(
            BigDecimal maxPrice
    ) {
        return (root, query, criteriaBuilder) -> {
            if (maxPrice == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.lessThanOrEqualTo(
                    root.get("price"),
                    maxPrice
            );
        };
    }

    private static String escapeLikePattern(
            String value
    ) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}