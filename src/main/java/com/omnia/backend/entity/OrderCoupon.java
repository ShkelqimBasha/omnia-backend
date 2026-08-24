package com.omnia.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "order_coupons",
        uniqueConstraints =
        @UniqueConstraint(
                name = "uq_order_coupons_order",
                columnNames = "order_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "order_id",
            nullable = false
    )
    private Order order;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "coupon_id",
            nullable = false
    )
    private Coupon coupon;

    @Column(
            name = "discount_amount",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal discountAmount;
}