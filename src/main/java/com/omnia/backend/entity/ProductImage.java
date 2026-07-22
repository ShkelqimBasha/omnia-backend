package com.omnia.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "product_id",
            nullable = false
    )
    private Product product;

    /*
     * New product images reference a securely uploaded file.
     *
     * The database unique index guarantees that one uploaded
     * file cannot be attached to multiple product images.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "uploaded_file_id",
            unique = true
    )
    private UploadedFile uploadedFile;

    /*
     * Kept temporarily for existing/legacy external URLs.
     * New product images must use uploadedFile instead.
     */
    @Column(
            name = "image_url",
            length = 500
    )
    private String legacyImageUrl;

    @Column(
            name = "is_primary",
            nullable = false
    )
    @Builder.Default
    private Boolean isPrimary = false;

    /*
     * Internal database uniqueness key.
     *
     * For a primary image it equals product.id.
     * For non-primary images it remains null.
     *
     * It must not be exposed through the API.
     */
    @Column(name = "primary_product_id")
    private Long primaryProductId;

    @Generated(event = EventType.INSERT)
    @Column(
            name = "created_at",
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @PrePersist
    @PreUpdate
    private void synchronizePrimaryProductId() {

        boolean primary =
                Boolean.TRUE.equals(isPrimary);

        isPrimary = primary;

        if (!primary) {
            primaryProductId = null;
            return;
        }

        if (product == null || product.getId() == null) {
            throw new IllegalStateException(
                    "Primary product image requires a persisted product"
            );
        }

        primaryProductId = product.getId();
    }

    public boolean isUploadedFileBacked() {
        return uploadedFile != null;
    }

    public boolean isLegacyUrlBacked() {
        return legacyImageUrl != null
                && !legacyImageUrl.isBlank();
    }
}