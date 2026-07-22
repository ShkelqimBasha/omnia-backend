package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.response.UserResponse;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.ProductImageRepository;
import com.omnia.backend.repository.UploadedFileRepository;
import com.omnia.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    private static final String USER_EMAIL =
            "avatar.user@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UploadedFileRepository uploadedFileRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    private UserProfileServiceImpl userProfileService;

    private User user;
    private UploadedFile uploadedFile;

    @BeforeEach
    void setUp() {

        userProfileService =
                new UserProfileServiceImpl(
                        userRepository,
                        uploadedFileRepository,
                        productImageRepository
                );

        Role role = Role.builder()
                .id(1L)
                .name("USER")
                .build();

        user = User.builder()
                .id(10L)
                .firstName("Avatar")
                .lastName("User")
                .username("avatar_user")
                .email(USER_EMAIL)
                .passwordHash("password-hash")
                .role(role)
                .emailVerified(true)
                .build();

        uploadedFile = UploadedFile.builder()
                .id(20L)
                .originalName("avatar.jpg")
                .storedName("stored-avatar.jpg")
                .contentType("image/jpeg")
                .size(100L)
                .checksumSha256("0".repeat(64))
                .uploadedBy(user)
                .uploadedAt(LocalDateTime.now())
                .build();

        authenticate(USER_EMAIL);

        lenient()
                .when(userRepository.findByEmail(USER_EMAIL))
                .thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateAvatar_WithOwnedJpeg_ShouldUpdateAvatar() {

        when(uploadedFileRepository.findById(20L))
                .thenReturn(Optional.of(uploadedFile));

        when(productImageRepository
                .existsByUploadedFileId(20L))
                .thenReturn(false);

        when(userRepository
                .existsByAvatarFileIdAndUserIdNot(
                        20L,
                        10L
                ))
                .thenReturn(false);

        when(userRepository.saveAndFlush(user))
                .thenReturn(user);

        UserResponse response =
                userProfileService.updateAvatar(20L);

        assertSame(
                uploadedFile,
                user.getAvatarFile()
        );
        assertNull(user.getLegacyProfileImage());

        assertEquals(10L, response.getId());
        assertEquals(20L, response.getAvatarFileId());
        assertEquals(
                "/api/files/20",
                response.getAvatarUrl()
        );

        verify(userRepository)
                .saveAndFlush(user);
    }

    @Test
    void updateAvatar_WithOwnedPng_ShouldUpdateAvatar() {

        uploadedFile.setContentType("image/png");
        uploadedFile.setStoredName("avatar.png");

        when(uploadedFileRepository.findById(20L))
                .thenReturn(Optional.of(uploadedFile));

        when(productImageRepository
                .existsByUploadedFileId(20L))
                .thenReturn(false);

        when(userRepository
                .existsByAvatarFileIdAndUserIdNot(
                        20L,
                        10L
                ))
                .thenReturn(false);

        when(userRepository.saveAndFlush(user))
                .thenReturn(user);

        UserResponse response =
                userProfileService.updateAvatar(20L);

        assertEquals(
                "/api/files/20",
                response.getAvatarUrl()
        );
    }

    @Test
    void updateAvatar_WithOwnedWebp_ShouldUpdateAvatar() {

        uploadedFile.setContentType("image/webp");
        uploadedFile.setStoredName("avatar.webp");

        when(uploadedFileRepository.findById(20L))
                .thenReturn(Optional.of(uploadedFile));

        when(productImageRepository
                .existsByUploadedFileId(20L))
                .thenReturn(false);

        when(userRepository
                .existsByAvatarFileIdAndUserIdNot(
                        20L,
                        10L
                ))
                .thenReturn(false);

        when(userRepository.saveAndFlush(user))
                .thenReturn(user);

        UserResponse response =
                userProfileService.updateAvatar(20L);

        assertEquals(20L, response.getAvatarFileId());
    }

    @Test
    void updateAvatar_ShouldRemoveLegacyAvatar() {

        user.setLegacyProfileImage(
                "https://legacy.example/avatar.jpg"
        );

        when(uploadedFileRepository.findById(20L))
                .thenReturn(Optional.of(uploadedFile));

        when(productImageRepository
                .existsByUploadedFileId(20L))
                .thenReturn(false);

        when(userRepository
                .existsByAvatarFileIdAndUserIdNot(
                        20L,
                        10L
                ))
                .thenReturn(false);

        when(userRepository.saveAndFlush(user))
                .thenReturn(user);

        userProfileService.updateAvatar(20L);

        assertNull(user.getLegacyProfileImage());
        assertSame(
                uploadedFile,
                user.getAvatarFile()
        );
    }

    @Test
    void updateAvatar_WhenFileDoesNotExist_ShouldThrowException() {

        when(uploadedFileRepository.findById(999L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> userProfileService
                                .updateAvatar(999L)
                );

        assertEquals(
                "Uploaded file not found",
                exception.getMessage()
        );

        verify(userRepository, never())
                .saveAndFlush(any(User.class));
    }

    @Test
    void updateAvatar_WhenFileBelongsToAnotherUser_ShouldDenyAccess() {

        User anotherUser = User.builder()
                .id(99L)
                .username("another_user")
                .email("another@example.com")
                .build();

        uploadedFile.setUploadedBy(anotherUser);

        when(uploadedFileRepository.findById(20L))
                .thenReturn(Optional.of(uploadedFile));

        AccessDeniedException exception =
                assertThrows(
                        AccessDeniedException.class,
                        () -> userProfileService
                                .updateAvatar(20L)
                );

        assertEquals(
                "You can only use your own "
                        + "uploaded file as avatar",
                exception.getMessage()
        );

        verifyNoInteractions(
                productImageRepository
        );

        verify(userRepository, never())
                .saveAndFlush(any(User.class));
    }

    @Test
    void updateAvatar_WhenFileHasNoOwner_ShouldDenyAccess() {

        uploadedFile.setUploadedBy(null);

        when(uploadedFileRepository.findById(20L))
                .thenReturn(Optional.of(uploadedFile));

        assertThrows(
                AccessDeniedException.class,
                () -> userProfileService
                        .updateAvatar(20L)
        );

        verify(userRepository, never())
                .saveAndFlush(any(User.class));
    }

    @Test
    void updateAvatar_WhenFileIsProductImage_ShouldThrowException() {

        when(uploadedFileRepository.findById(20L))
                .thenReturn(Optional.of(uploadedFile));

        when(productImageRepository
                .existsByUploadedFileId(20L))
                .thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> userProfileService
                                .updateAvatar(20L)
                );

        assertEquals(
                "Uploaded file is already attached "
                        + "to a product image",
                exception.getMessage()
        );

        verify(userRepository, never())
                .saveAndFlush(any(User.class));
    }

    @Test
    void updateAvatar_WhenUsedByAnotherUser_ShouldThrowException() {

        when(uploadedFileRepository.findById(20L))
                .thenReturn(Optional.of(uploadedFile));

        when(productImageRepository
                .existsByUploadedFileId(20L))
                .thenReturn(false);

        when(userRepository
                .existsByAvatarFileIdAndUserIdNot(
                        20L,
                        10L
                ))
                .thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> userProfileService
                                .updateAvatar(20L)
                );

        assertEquals(
                "Uploaded file is already used "
                        + "as another user's avatar",
                exception.getMessage()
        );

        verify(userRepository, never())
                .saveAndFlush(any(User.class));
    }

    @Test
    void updateAvatar_WithUnsupportedContentType_ShouldThrowException() {

        uploadedFile.setContentType(
                "application/pdf"
        );

        when(uploadedFileRepository.findById(20L))
                .thenReturn(Optional.of(uploadedFile));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> userProfileService
                                .updateAvatar(20L)
                );

        assertEquals(
                "Avatar must be a JPEG, PNG "
                        + "or WEBP image",
                exception.getMessage()
        );

        verifyNoInteractions(
                productImageRepository
        );

        verify(userRepository, never())
                .saveAndFlush(any(User.class));
    }

    @Test
    void updateAvatar_WithInvalidFileId_ShouldThrowException() {

        assertThrows(
                IllegalArgumentException.class,
                () -> userProfileService
                        .updateAvatar(0L)
        );

        verifyNoInteractions(
                uploadedFileRepository,
                productImageRepository
        );

        verify(userRepository, never())
                .saveAndFlush(any(User.class));
    }

    @Test
    void updateAvatar_WithoutAuthentication_ShouldDenyAccess() {

        SecurityContextHolder.clearContext();

        assertThrows(
                AccessDeniedException.class,
                () -> userProfileService
                        .updateAvatar(20L)
        );

        verifyNoInteractions(
                userRepository,
                uploadedFileRepository,
                productImageRepository
        );
    }

    @Test
    void updateAvatar_WhenAuthenticatedUserIsMissing_ShouldThrowException() {

        when(userRepository.findByEmail(USER_EMAIL))
                .thenReturn(Optional.empty());

        when(userRepository.findByUsername(USER_EMAIL))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> userProfileService
                        .updateAvatar(20L)
        );

        verifyNoInteractions(
                uploadedFileRepository,
                productImageRepository
        );
    }

    @Test
    void removeAvatar_ShouldRemoveUploadedAndLegacyAvatar() {

        user.setAvatarFile(uploadedFile);
        user.setLegacyProfileImage(null);

        when(userRepository.saveAndFlush(user))
                .thenReturn(user);

        UserResponse response =
                userProfileService.removeAvatar();

        assertNull(user.getAvatarFile());
        assertNull(user.getLegacyProfileImage());
        assertNull(response.getAvatarFileId());
        assertNull(response.getAvatarUrl());

        verify(userRepository)
                .saveAndFlush(user);

        verifyNoInteractions(
                uploadedFileRepository,
                productImageRepository
        );
    }

    @Test
    void removeAvatar_WhenUserHasLegacyAvatar_ShouldRemoveIt() {

        user.setLegacyProfileImage(
                "https://legacy.example/avatar.jpg"
        );

        when(userRepository.saveAndFlush(user))
                .thenReturn(user);

        UserResponse response =
                userProfileService.removeAvatar();

        assertNull(user.getLegacyProfileImage());
        assertNull(response.getAvatarUrl());
    }

    @Test
    void removeAvatar_WithoutAuthentication_ShouldDenyAccess() {

        SecurityContextHolder.clearContext();

        assertThrows(
                AccessDeniedException.class,
                () -> userProfileService.removeAvatar()
        );

        verifyNoInteractions(
                userRepository,
                uploadedFileRepository,
                productImageRepository
        );
    }

    private void authenticate(String principal) {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        AuthorityUtils.createAuthorityList(
                                "ROLE_USER"
                        )
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }
}