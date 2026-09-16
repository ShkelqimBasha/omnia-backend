package com.omnia.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "checkout_groups",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_checkout_groups_reference",
                columnNames = "checkout_reference"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "checkout_reference",
            nullable = false,
            unique = true,
            length = 36
    )
    private String checkoutReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Column(name = "order_count", nullable = false)
    private Integer orderCount = 0;

    @Builder.Default
    @Column(
            name = "subtotal_amount",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(
            name = "shipping_fee",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Builder.Default
    @Column(
            name = "discount_amount",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(
            name = "total_amount",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(
            name = "created_at",
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @PrePersist
    private void ensureCheckoutReference() {
        if (checkoutReference == null
                || checkoutReference.isBlank()) {
            checkoutReference = UUID.randomUUID().toString();
        }
    }
}