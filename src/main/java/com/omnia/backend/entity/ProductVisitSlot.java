package com.omnia.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A time a property seller is available for a visit. Booked by at most one "Kërko vizitë" inquiry. */
@Entity
@Table(name = "product_visit_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVisitSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Local time in Europe/Tirane, minute precision. */
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inquiry_id")
    private ProductInquiry inquiry;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
