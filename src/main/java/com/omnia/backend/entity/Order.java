package com.omnia.backend.entity;

import com.omnia.backend.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_group_id")
    private CheckoutGroup checkoutGroup;

    @Column(name = "checkout_sequence")
    private Integer checkoutSequence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(name = "organization_name", length = 150)
    private String organizationName;

    @Column(name = "address_id")
    private Long addressId;

    @Column(name = "shipping_name", length = 150)
    private String shippingName;

    @Column(name = "shipping_email", length = 150)
    private String shippingEmail;

    @Column(name = "shipping_phone", length = 30)
    private String shippingPhone;

    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    @Column(name = "order_notes", length = 500)
    private String orderNotes;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, length = 20)
    private com.omnia.backend.enums.DeliveryMethod deliveryMethod = com.omnia.backend.enums.DeliveryMethod.STANDARD;

    @Column(name = "shipping_latitude", precision = 10, scale = 7)
    private BigDecimal shippingLatitude;

    @Column(name = "shipping_longitude", precision = 10, scale = 7)
    private BigDecimal shippingLongitude;

    @Builder.Default
    @Column(name = "express_surcharge", nullable = false, precision = 10, scale = 2)
    private BigDecimal expressSurcharge = BigDecimal.ZERO;


    @Column(
            name = "total_amount",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal totalAmount;

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

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(
            name = "created_at",
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;
}