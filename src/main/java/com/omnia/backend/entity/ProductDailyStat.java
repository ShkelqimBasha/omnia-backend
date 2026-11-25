package com.omnia.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Read-only view of {@code product_daily_stats} (written by ProductActivityCounter with JDBC).
 * Mapped only so catalog queries can sort by recent views in a subquery.
 */
@Entity
@Table(name = "product_daily_stats")
@org.hibernate.annotations.Immutable
@Getter
@NoArgsConstructor
public class ProductDailyStat {

    @EmbeddedId
    private Key id;

    @Column(name = "views", nullable = false)
    private Integer views;

    @Embeddable
    @Getter
    @NoArgsConstructor
    public static class Key implements Serializable {

        @Column(name = "product_id", nullable = false)
        private Long productId;

        @Column(name = "stat_date", nullable = false)
        private LocalDate statDate;

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(productId, key.productId) && Objects.equals(statDate, key.statDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productId, statDate);
        }
    }
}
