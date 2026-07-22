package com.omnia.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnia.backend.dto.request.UserAvatarRequest;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.UploadedFileRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.repository.RoleRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockUser(
        username = "profile.user@example.com",
        roles = "USER"
)
class UserProfileControllerIntegrationTest
        extends AbstractIntegrationTest {

    private static final String USER_EMAIL =
            "profile.user@example.com";

    private static final String USERNAME =
            "profile_user";

    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF,
            (byte) 0xD8,
            (byte) 0xFF,
            (byte) 0xE0,
            0x00,
            0x10,
            0x4A,
            0x46,
            0x49,
            0x46,
            0x00,
            0x01,
            0x01,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x01,
            0x00,
            0x01,
            0x00,
            0x00,
            (byte) 0xFF,
            (byte) 0xD9
    };

    private static Path uploadDirectory;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    private User user;

    @DynamicPropertySource
    static void configureFileStorage(
            DynamicPropertyRegistry registry
    ) {
        if (uploadDirectory == null) {
            try {
                uploadDirectory =
                        Files.createTempDirectory(
                                "omnia-avatar-integration-"
                        );
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Could not create test upload directory",
                        exception
                );
            }
        }

        registry.add(
                "app.file.upload-dir",
                () -> uploadDirectory.toString()
        );

        registry.add(
                "app.file.max-size",
                () -> "5MB"
        );

        registry.add(
                "spring.servlet.multipart.max-file-size",
                () -> "5MB"
        );

        registry.add(
                "spring.servlet.multipart.max-request-size",
                () -> "6MB"
        );
    }

    @BeforeEach
    void setUp() throws IOException {

        cleanDatabase();
        deletePhysicalFiles();

        Role userRole = roleRepository
                .findByName("USER")
                .orElseThrow(() ->
                        new IllegalStateException(
                                "USER role is missing"
                        )
                );

        user = User.builder()
                .firstName("Profile")
                .lastName("User")
                .username(USERNAME)
                .email(USER_EMAIL)
                .passwordHash(
                        "$2a$10$integration.test.password.hash"
                )
                .role(userRole)
                .emailVerified(true)
                .build();

        user = userRepository.saveAndFlush(user);
    }

    @AfterEach
    void cleanUp() throws IOException {

        cleanDatabase();
        deletePhysicalFiles();
    }

    @AfterAll
    static void deleteUploadDirectory()
            throws IOException {

        if (uploadDirectory == null
                || !Files.exists(uploadDirectory)) {
            return;
        }

        try (var paths = Files.walk(uploadDirectory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException(
                                    "Could not delete test path: "
                                            + path,
                                    exception
                            );
                        }
                    });
        }
    }

    @Test
    void uploadAndAssignAvatar_ShouldUpdateCurrentUser()
            throws Exception {

        Long uploadedFileId =
                uploadAvatarAndGetId();

        UserAvatarRequest request =
                UserAvatarRequest.builder()
                        .uploadedFileId(uploadedFileId)
                        .build();

        mockMvc.perform(
                        put("/api/users/me/avatar")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(user.getId())
                )
                .andExpect(
                        jsonPath("$.email")
                                .value(USER_EMAIL)
                )
                .andExpect(
                        jsonPath("$.avatarFileId")
                                .value(uploadedFileId)
                )
                .andExpect(
                        jsonPath("$.avatarUrl")
                                .value(
                                        "/api/files/"
                                                + uploadedFileId
                                )
                );

        User persistedUser =
                userRepository.findById(user.getId())
                        .orElseThrow();

        assertThat(
                persistedUser.getAvatarFile().getId()
        ).isEqualTo(uploadedFileId);
    }

    @Test
    void currentUser_AfterAssigningAvatar_ShouldReturnAvatar()
            throws Exception {

        Long uploadedFileId =
                uploadAvatarAndGetId();

        assignAvatar(uploadedFileId);

        mockMvc.perform(
                        get("/api/auth/me")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(user.getId())
                )
                .andExpect(
                        jsonPath("$.username")
                                .value(USERNAME)
                )
                .andExpect(
                        jsonPath("$.email")
                                .value(USER_EMAIL)
                )
                .andExpect(
                        jsonPath("$.role")
                                .value("USER")
                )
                .andExpect(
                        jsonPath("$.avatarFileId")
                                .value(uploadedFileId)
                )
                .andExpect(
                        jsonPath("$.avatarUrl")
                                .value(
                                        "/api/files/"
                                                + uploadedFileId
                                )
                );
    }

    @Test
    void removeAvatar_ShouldRemoveRelationAndRetainFile()
            throws Exception {

        Long uploadedFileId =
                uploadAvatarAndGetId();

        assignAvatar(uploadedFileId);

        mockMvc.perform(
                        delete("/api/users/me/avatar")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.avatarFileId")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.avatarUrl")
                                .doesNotExist()
                );

        User persistedUser =
                userRepository.findById(user.getId())
                        .orElseThrow();

        assertThat(
                persistedUser.getAvatarFile()
        ).isNull();

        assertThat(
                uploadedFileRepository
                        .existsById(uploadedFileId)
        ).isTrue();

        assertThat(countPhysicalFiles())
                .isEqualTo(1);
    }

    @Test
    void assignAvatar_WithMissingFile_ShouldReturnNotFound()
            throws Exception {

        UserAvatarRequest request =
                UserAvatarRequest.builder()
                        .uploadedFileId(999999L)
                        .build();

        mockMvc.perform(
                        put("/api/users/me/avatar")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Uploaded file not found"
                                )
                );
    }

    @Test
    void assignAvatar_WithNullFileId_ShouldReturnBadRequest()
            throws Exception {

        UserAvatarRequest request =
                UserAvatarRequest.builder()
                        .uploadedFileId(null)
                        .build();

        mockMvc.perform(
                        put("/api/users/me/avatar")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignAvatar_WithFileOwnedByAnotherUser_ShouldReturnForbidden()
            throws Exception {

        Role userRole = roleRepository
                .findByName("USER")
                .orElseThrow();

        User anotherUser = User.builder()
                .firstName("Another")
                .lastName("User")
                .username("another_avatar_user")
                .email("another.avatar@example.com")
                .passwordHash("password-hash")
                .role(userRole)
                .emailVerified(true)
                .build();

        anotherUser =
                userRepository.saveAndFlush(
                        anotherUser
                );

        UploadedFile anotherUsersFile =
                UploadedFile.builder()
                        .originalName("other.jpg")
                        .storedName("other-avatar.jpg")
                        .contentType("image/jpeg")
                        .size((long) JPEG_BYTES.length)
                        .checksumSha256("0".repeat(64))
                        .uploadedBy(anotherUser)
                        .uploadedAt(LocalDateTime.now())
                        .build();

        anotherUsersFile =
                uploadedFileRepository.saveAndFlush(
                        anotherUsersFile
                );

        UserAvatarRequest request =
                UserAvatarRequest.builder()
                        .uploadedFileId(
                                anotherUsersFile.getId()
                        )
                        .build();

        mockMvc.perform(
                        put("/api/users/me/avatar")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void assignAvatar_WithoutAuthentication_ShouldReturnForbidden()
            throws Exception {

        Long uploadedFileId =
                uploadAvatarAndGetId();

        UserAvatarRequest request =
                UserAvatarRequest.builder()
                        .uploadedFileId(uploadedFileId)
                        .build();

        mockMvc.perform(
                        put("/api/users/me/avatar")
                                .with(
                                        org.springframework.security
                                                .test.web.servlet
                                                .request
                                                .SecurityMockMvcRequestPostProcessors
                                                .anonymous()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isForbidden());
    }

    private Long uploadAvatarAndGetId()
            throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "avatar.jpg",
                        "image/jpeg",
                        JPEG_BYTES
                );

        MvcResult result =
                mockMvc.perform(
                                multipart("/api/files/upload")
                                        .file(file)
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        JsonNode response =
                objectMapper.readTree(
                        result.getResponse()
                                .getContentAsString()
                );

        return response.get("id").asLong();
    }

    private void assignAvatar(
            Long uploadedFileId
    ) throws Exception {

        UserAvatarRequest request =
                UserAvatarRequest.builder()
                        .uploadedFileId(uploadedFileId)
                        .build();

        mockMvc.perform(
                        put("/api/users/me/avatar")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk());
    }

    private void cleanDatabase() {

        /*
         * Users reference avatar files, while uploaded files
         * reference their uploader. Delete users first so the
         * avatar relationship is removed and uploaded_by becomes
         * NULL through its foreign-key rule.
         */
        userRepository.deleteAll();
        uploadedFileRepository.deleteAll();
    }

    private static void deletePhysicalFiles()
            throws IOException {

        if (uploadDirectory == null
                || !Files.exists(uploadDirectory)) {
            return;
        }

        try (var files = Files.list(uploadDirectory)) {
            for (Path path : files.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static long countPhysicalFiles()
            throws IOException {

        if (uploadDirectory == null
                || !Files.exists(uploadDirectory)) {
            return 0;
        }

        try (var files = Files.list(uploadDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .count();
        }
    }
}