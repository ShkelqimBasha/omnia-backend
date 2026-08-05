package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.CurrentUserOrganizationResponse;
import com.omnia.backend.dto.response.UserResponse;
import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.OrganizationMemberRole;
import com.omnia.backend.enums.OrganizationMemberStatus;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class UserMapper {

    private static final String FILE_ENDPOINT =
            "/api/files/";

    private static final Set<String>
            PLATFORM_ADMIN_ROLES =
            Set.of(
                    "ADMIN",
                    "SUPER_ADMIN"
            );

    private static final Set<OrganizationMemberRole>
            ORGANIZATION_ADMIN_ROLES =
            Set.of(
                    OrganizationMemberRole.OWNER,
                    OrganizationMemberRole.ADMIN
            );

    public static UserResponse toResponse(
            User user
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "User must not be null"
            );
        }

        UploadedFile avatarFile =
                user.getAvatarFile();

        Long avatarFileId =
                avatarFile == null
                        ? null
                        : avatarFile.getId();

        String roleName =
                user.getRole() == null
                        ? null
                        : user.getRole().getName();

        boolean platformAdmin =
                roleName != null
                        && PLATFORM_ADMIN_ROLES.contains(
                        roleName.trim()
                                .toUpperCase(Locale.ROOT)
                );

        List<OrganizationMember> memberships =
                user.getOrganizationMemberships() == null
                        ? List.of()
                        : user.getOrganizationMemberships();

        List<CurrentUserOrganizationResponse>
                organizationResponses =
                memberships.stream()
                        .filter(UserMapper::isUsableMembership)
                        .map(UserMapper::toOrganizationResponse)
                        .toList();

        boolean organizationAdmin =
                memberships.stream()
                        .filter(UserMapper::isUsableMembership)
                        .anyMatch(member ->
                                ORGANIZATION_ADMIN_ROLES
                                        .contains(
                                                member.getMembershipRole()
                                        )
                        );

        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(roleName)
                .status(
                        user.getStatus() == null
                                ? null
                                : user.getStatus().name()
                )
                .emailVerified(user.getEmailVerified())
                .platformAdmin(platformAdmin)
                .organizationAdmin(organizationAdmin)
                .hasAdminAccess(
                        platformAdmin
                                || organizationAdmin
                )
                .avatarFileId(avatarFileId)
                .avatarUrl(
                        resolveAvatarUrl(
                                user,
                                avatarFileId
                        )
                )
                .organizations(organizationResponses)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private static boolean isUsableMembership(
            OrganizationMember member
    ) {
        return member != null
                && member.getOrganization() != null
                && member.getOrganization().isActive()
                && OrganizationMemberStatus.ACTIVE.equals(
                member.getStatus()
        );
    }

    private static CurrentUserOrganizationResponse
    toOrganizationResponse(
            OrganizationMember member
    ) {
        return CurrentUserOrganizationResponse
                .builder()
                .organizationId(
                        member.getOrganization().getId()
                )
                .organizationName(
                        member.getOrganization().getName()
                )
                .organizationSlug(
                        member.getOrganization().getSlug()
                )
                .organizationStatus(
                        member.getOrganization()
                                .getStatus()
                                .name()
                )
                .membershipRole(
                        member.getMembershipRole().name()
                )
                .membershipStatus(
                        member.getStatus().name()
                )
                .build();
    }

    private static String resolveAvatarUrl(
            User user,
            Long avatarFileId
    ) {
        if (avatarFileId != null) {
            return FILE_ENDPOINT + avatarFileId;
        }

        if (user.hasLegacyAvatar()) {
            return user.getLegacyProfileImage();
        }

        return null;
    }

    private UserMapper() {
    }
}