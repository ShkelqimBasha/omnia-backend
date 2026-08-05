package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.AdminUserResponse;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.entity.User;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class AdminUserMapper {

    private static final String FILE_ENDPOINT =
            "/api/files/";

    private static final Set<String>
            PLATFORM_ADMIN_ROLES =
            Set.of(
                    "SUPER_ADMIN",
                    "ADMIN"
            );

    public AdminUserResponse toResponse(
            User user,
            boolean organizationAdmin
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

        return AdminUserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(
                        user.getStatus() == null
                                ? null
                                : user.getStatus().name()
                )
                .emailVerified(user.getEmailVerified())
                .platformRole(roleName)
                .organizationAdmin(
                        organizationAdmin
                )
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
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private String resolveAvatarUrl(
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
}