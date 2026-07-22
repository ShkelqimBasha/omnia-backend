package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.UserResponse;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserMapperTest {

    @Test
    void toResponse_WithUploadedAvatar_ShouldMapFileUrl() {

        Role role = createRole();

        UploadedFile avatarFile =
                UploadedFile.builder()
                        .id(50L)
                        .build();

        User user = createUser(role);
        user.setAvatarFile(avatarFile);
        user.setLegacyProfileImage(
                "/legacy/avatar.png"
        );

        UserResponse response =
                UserMapper.toResponse(user);

        assertCommonFields(response);

        assertThat(response.getRole())
                .isEqualTo("USER");

        assertThat(response.getAvatarFileId())
                .isEqualTo(50L);

        assertThat(response.getAvatarUrl())
                .isEqualTo("/api/files/50");
    }

    @Test
    void toResponse_WithLegacyAvatar_ShouldMapLegacyUrl() {

        User user = createUser(createRole());

        user.setAvatarFile(null);
        user.setLegacyProfileImage(
                "/legacy/avatar.png"
        );

        UserResponse response =
                UserMapper.toResponse(user);

        assertCommonFields(response);

        assertThat(response.getRole())
                .isEqualTo("USER");

        assertThat(response.getAvatarFileId())
                .isNull();

        assertThat(response.getAvatarUrl())
                .isEqualTo("/legacy/avatar.png");
    }

    @Test
    void toResponse_WithoutAvatar_ShouldMapNullAvatarFields() {

        User user = createUser(createRole());

        user.setAvatarFile(null);
        user.setLegacyProfileImage(null);

        UserResponse response =
                UserMapper.toResponse(user);

        assertCommonFields(response);

        assertThat(response.getAvatarFileId())
                .isNull();

        assertThat(response.getAvatarUrl())
                .isNull();
    }

    @Test
    void toResponse_WithoutRole_ShouldMapNullRole() {

        User user = createUser(null);

        UserResponse response =
                UserMapper.toResponse(user);

        assertCommonFields(response);

        assertThat(response.getRole())
                .isNull();

        assertThat(response.getAvatarFileId())
                .isNull();

        assertThat(response.getAvatarUrl())
                .isNull();
    }

    @Test
    void toResponse_WithNullUser_ShouldThrowException() {

        assertThatThrownBy(
                () -> UserMapper.toResponse(null)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "User must not be null"
                );
    }

    private User createUser(Role role) {

        return User.builder()
                .id(10L)
                .firstName("Test")
                .lastName("User")
                .username("test-user")
                .email("test@example.com")
                .passwordHash("encoded-password")
                .role(role)
                .avatarFile(null)
                .legacyProfileImage(null)
                .build();
    }

    private Role createRole() {

        return Role.builder()
                .id(1L)
                .name("USER")
                .description("Standard user")
                .build();
    }

    private void assertCommonFields(
            UserResponse response
    ) {

        assertThat(response)
                .isNotNull();

        assertThat(response.getId())
                .isEqualTo(10L);

        assertThat(response.getFirstName())
                .isEqualTo("Test");

        assertThat(response.getLastName())
                .isEqualTo("User");

        assertThat(response.getUsername())
                .isEqualTo("test-user");

        assertThat(response.getEmail())
                .isEqualTo("test@example.com");
    }
}