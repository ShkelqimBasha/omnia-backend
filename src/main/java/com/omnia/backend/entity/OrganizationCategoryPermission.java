package com.omnia.backend.entity;

import com.omnia.backend.enums.OrganizationPermissionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "organization_category_permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_org_category_permissions",
                        columnNames = {
                                "organization_id",
                                "category_id"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationCategoryPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "category_id",
            nullable = false
    )
    private Category category;

    @Builder.Default
    @Column(
            name = "can_create",
            nullable = false
    )
    private Boolean canCreate = true;

    @Builder.Default
    @Column(
            name = "can_update",
            nullable = false
    )
    private Boolean canUpdate = true;

    @Builder.Default
    @Column(
            name = "can_delete",
            nullable = false
    )
    private Boolean canDelete = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private OrganizationPermissionStatus status =
            OrganizationPermissionStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private User grantedBy;

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

    public boolean isActive() {
        return OrganizationPermissionStatus.ACTIVE.equals(status);
    }

    public boolean allowsCreate() {
        return isActive()
                && Boolean.TRUE.equals(canCreate);
    }

    public boolean allowsUpdate() {
        return isActive()
                && Boolean.TRUE.equals(canUpdate);
    }

    public boolean allowsDelete() {
        return isActive()
                && Boolean.TRUE.equals(canDelete);
    }
}