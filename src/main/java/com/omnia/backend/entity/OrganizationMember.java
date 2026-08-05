package com.omnia.backend.entity;

import com.omnia.backend.enums.OrganizationMemberRole;
import com.omnia.backend.enums.OrganizationMemberStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "organization_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_organization_members_org_user",
                        columnNames = {
                                "organization_id",
                                "user_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_organization_members_user_status",
                        columnList = "user_id,status"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationMember {

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
            name = "user_id",
            nullable = false
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(
            name = "membership_role",
            nullable = false
    )
    private OrganizationMemberRole membershipRole =
            OrganizationMemberRole.STAFF;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private OrganizationMemberStatus status =
            OrganizationMemberStatus.ACTIVE;

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

    public boolean isActive() {
        return OrganizationMemberStatus.ACTIVE.equals(status);
    }

    public boolean canManageProducts() {
        return OrganizationMemberRole.OWNER.equals(membershipRole)
                || OrganizationMemberRole.ADMIN.equals(membershipRole);
    }

    public boolean canManageMembers() {
        return OrganizationMemberRole.OWNER.equals(membershipRole);
    }
}