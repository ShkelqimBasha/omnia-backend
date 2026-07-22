package com.omnia.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "uploaded_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadedFile {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @Column(
            name = "original_name",
            nullable = false,
            length = 255
    )
    private String originalName;

    @Column(
            name = "stored_name",
            nullable = false,
            unique = true,
            length = 100
    )
    private String storedName;

    @Column(
            name = "content_type",
            nullable = false,
            length = 100
    )
    private String contentType;

    @Column(nullable = false)
    private Long size;

    /*
     * Nullable vetëm për upload-et legacy që ekzistonin
     * përpara migrimit V9. Çdo upload i ri e ruan checksum-in.
     */
    @Column(
            name = "checksum_sha256",
            length = 64,
            columnDefinition = "CHAR(64)"
    )
    private String checksumSha256;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(
            name = "uploaded_at",
            nullable = false
    )
    private LocalDateTime uploadedAt;
}