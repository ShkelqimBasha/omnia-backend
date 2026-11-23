package com.omnia.backend.entity;

import com.omnia.backend.enums.InquiryStatus;
import com.omnia.backend.enums.InquiryType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A buyer's "Kontakto" or "Kërko vizitë" request on a car or property listing. */
@Entity
@Table(name = "product_inquiries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryType type;

    @Column(name = "contact_name", nullable = false, length = 120)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 40)
    private String contactPhone;

    @Column(length = 1000)
    private String message;

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    /** The chosen visit slot's time (local, Europe/Tirane), when the buyer picked one. */
    @Column(name = "visit_at")
    private LocalDateTime visitAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InquiryStatus status = InquiryStatus.NEW;

    @Column(name = "status_updated_at")
    private LocalDateTime statusUpdatedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
