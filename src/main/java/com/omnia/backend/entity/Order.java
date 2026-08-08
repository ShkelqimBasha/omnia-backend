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

    // Përdoruesi që ka bërë porosinë
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "address_id")
    private Long addressId;

    @Column(
            name = "shipping_name",
            length = 150
    )
    private String shippingName;

    @Column(
            name = "shipping_email",
            length = 150
    )
    private String shippingEmail;

    @Column(
            name = "shipping_phone",
            length = 30
    )
    private String shippingPhone;

    @Column(
            name = "shipping_address",
            length = 500
    )
    private String shippingAddress;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}