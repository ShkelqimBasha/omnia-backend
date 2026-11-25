package com.omnia.backend.service.impl;

import com.omnia.backend.dto.response.ProductResponse;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Fills averageRating / reviewCount (visible reviews only) for a page of products in one query. */
@Component
public class ProductRatings {

    private final EntityManager entityManager;

    public ProductRatings(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void fill(List<ProductResponse> products) {
        if (products == null || products.isEmpty()) return;
        List<Long> ids = products.stream().map(ProductResponse::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return;
        Map<Long, Object[]> byProduct = new HashMap<>();
        for (Object[] row : entityManager.createQuery("""
                        select r.product.id, avg(r.rating), count(r)
                        from Review r
                        where r.product.id in :ids and r.hidden = false
                        group by r.product.id
                        """, Object[].class)
                .setParameter("ids", ids)
                .getResultList()) {
            byProduct.put((Long) row[0], row);
        }
        for (ProductResponse product : products) {
            Object[] row = byProduct.get(product.getId());
            if (row == null) {
                product.setAverageRating(null);
                product.setReviewCount(0L);
            } else {
                product.setAverageRating(BigDecimal.valueOf(((Number) row[1]).doubleValue())
                        .setScale(2, RoundingMode.HALF_UP));
                product.setReviewCount(((Number) row[2]).longValue());
            }
        }
    }
}
