package com.omnia.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/** A buyer's saved listing filters, with optional alerts about new matching products. */
@Entity
@Table(name = "saved_searches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    /** Normalized query parameters: keyword, categoryId, brand, minPrice, maxPrice, attr.*. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private Map<String, String> filters;

    @Column(name = "filters_hash", nullable = false, length = 64)
    private String filtersHash;

    @Builder.Default
    @Column(name = "alerts_enabled", nullable = false)
    private boolean alertsEnabled = true;

    /** Products with an id above this one are "new" for this search. */
    @Builder.Default
    @Column(name = "last_seen_product_id", nullable = false)
    private Long lastSeenProductId = 0L;

    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
