package com.omnia.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "checkout_group_coupons",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_checkout_group_coupons_group",
                columnNames = "checkout_group_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutGroupCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "checkout_group_id",
            nullable = false,
            unique = true
    )
    private CheckoutGroup checkoutGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Builder.Default
    @Column(
            name = "discount_amount",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal discountAmount = BigDecimal.ZERO;
}