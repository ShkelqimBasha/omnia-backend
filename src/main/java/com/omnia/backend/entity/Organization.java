package com.omnia.backend.entity;

import com.omnia.backend.enums.OrganizationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            nullable = false,
            length = 150
    )
    private String name;

    @Column(
            nullable = false,
            unique = true,
            length = 160
    )
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private OrganizationStatus status =
            OrganizationStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(
            name = "created_at",
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            insertable = false,
            updatable = false
    )
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_uploaded_file_id")
    private UploadedFile logoFile;

    @Column(name="business_email",length=150)
    private String businessEmail;
    @Column(length=30)
    private String phone;
    @Column(length=500)
    private String address;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="primary_administrator_id")
    private User primaryAdministrator;
    @Column private Double latitude;
    @Column private Double longitude;
    @Column(name="editor_name_key",length=150,unique=true)
    private String editorNameKey;
    @PreUpdate
    private void synchronizeEditorName() {
        if(editorNameKey!=null&&name!=null)editorNameKey=name.trim().replaceAll("\\s+"," ").toLowerCase(java.util.Locale.ROOT);
    }
    @Version @Builder.Default
    @Column(name="editor_revision",nullable=false)
    private Long editorRevision=0L;

    public boolean isActive() {
        return OrganizationStatus.ACTIVE.equals(status);
    }
}
