package com.omnia.backend.entity;

import com.omnia.backend.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "category_id",
            nullable = false
    )
    private Category category;

    /*
     * Nullable temporarily for products that existed
     * before organization support was introduced.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    /*
     * User who originally created the product.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(
            nullable = false,
            length = 150
    )
    private String name;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 50)
    @Builder.Default
    private java.util.List<ProductImage> images = new java.util.ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String brand;

    /** Category-specific listing fields (size, year, area …); see CategoryFieldCatalog. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private java.util.Map<String, Object> attributes;

    /** Cars only: CONTACT_ONLY or CONTACT_AND_BUY. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sale_mode", length = 20)
    private com.omnia.backend.enums.SaleMode saleMode;

    @Column(
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal price;

    @Column(
            name = "discount_price",
            precision = 10,
            scale = 2
    )
    private BigDecimal discountPrice;

    @Column(nullable = false)
    private Integer stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(
            name = "created_at",
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            insertable = false,
            updatable = false
    )
    private LocalDateTime updatedAt;
}