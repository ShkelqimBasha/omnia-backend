package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.UserResponse;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.entity.User;

public final class UserMapper {

    private static final String FILE_ENDPOINT =
            "/api/files/";

    public static UserResponse toResponse(User user) {

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

        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(
                        user.getRole() == null
                                ? null
                                : user.getRole().getName()
                )
                .avatarFileId(avatarFileId)
                .avatarUrl(
                        resolveAvatarUrl(
                                user,
                                avatarFileId
                        )
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