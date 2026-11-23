package com.omnia.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User që ka bërë review
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Produkti
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Builder.Default
    @Column(nullable = false)
    private boolean hidden = false;

    @Column(columnDefinition = "TEXT")
    private String adminReply;

    @Column(length = 100)
    private String repliedBy;

    private LocalDateTime repliedAt;

    /** The author had a DELIVERED order of this product when the review was written ("Blerje e verifikuar"). */
    @Builder.Default
    @Column(name = "verified_purchase", nullable = false)
    private boolean verifiedPurchase = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}