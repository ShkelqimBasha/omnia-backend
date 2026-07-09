package com.omnia.backend.specification;

import com.omnia.backend.entity.Product;
import com.omnia.backend.enums.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public class ProductSpecification {

    public static Specification<Product> filterProducts(
            String keyword,
            Long categoryId,
            String brand,
            ProductStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        return Specification
                .where(containsKeyword(keyword))
                .and(hasCategory(categoryId))
                .and(hasBrand(brand))
                .and(hasStatus(status))
                .and(priceGreaterThanOrEqual(minPrice))
                .and(priceLessThanOrEqual(maxPrice));
    }

    private static Specification<Product> containsKeyword(String keyword) {
        return (root, query, criteriaBuilder) -> {
            if (keyword == null || keyword.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }

            String likePattern = "%" + keyword.toLowerCase() + "%";

            return criteriaBuilder.or(
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("name")),
                            likePattern
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("description")),
                            likePattern
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("brand")),
                            likePattern
                    )
            );
        };
    }

    private static Specification<Product> hasCategory(Long categoryId) {
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

    private static Specification<Product> hasBrand(String brand) {
        return (root, query, criteriaBuilder) -> {
            if (brand == null || brand.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get("brand")),
                    brand.toLowerCase()
            );
        };
    }

    private static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    private static Specification<Product> priceGreaterThanOrEqual(BigDecimal minPrice) {
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

    private static Specification<Product> priceLessThanOrEqual(BigDecimal maxPrice) {
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

    private ProductSpecification() {
    }
}