package com.omnia.backend.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void hasUploadedAvatar_WithAvatarFile_ShouldReturnTrue() {

        User user = User.builder()
                .avatarFile(
                        UploadedFile.builder()
                                .id(10L)
                                .build()
                )
                .build();

        assertThat(user.hasUploadedAvatar())
                .isTrue();
    }

    @Test
    void hasUploadedAvatar_WithoutAvatarFile_ShouldReturnFalse() {

        User user = User.builder()
                .avatarFile(null)
                .build();

        assertThat(user.hasUploadedAvatar())
                .isFalse();
    }

    @Test
    void hasLegacyAvatar_WithValidUrl_ShouldReturnTrue() {

        User user = User.builder()
                .legacyProfileImage(
                        "/legacy/avatar.png"
                )
                .build();

        assertThat(user.hasLegacyAvatar())
                .isTrue();
    }

    @Test
    void hasLegacyAvatar_WithNullUrl_ShouldReturnFalse() {

        User user = User.builder()
                .legacyProfileImage(null)
                .build();

        assertThat(user.hasLegacyAvatar())
                .isFalse();
    }

    @Test
    void hasLegacyAvatar_WithEmptyUrl_ShouldReturnFalse() {

        User user = User.builder()
                .legacyProfileImage("")
                .build();

        assertThat(user.hasLegacyAvatar())
                .isFalse();
    }

    @Test
    void hasLegacyAvatar_WithWhitespaceUrl_ShouldReturnFalse() {

        User user = User.builder()
                .legacyProfileImage("   ")
                .build();

        assertThat(user.hasLegacyAvatar())
                .isFalse();
    }
}