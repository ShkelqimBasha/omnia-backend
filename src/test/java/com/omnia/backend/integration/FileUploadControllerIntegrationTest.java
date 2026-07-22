package com.omnia.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UploadedFileRepository;
import com.omnia.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FileUploadControllerIntegrationTest
        extends AbstractIntegrationTest {

    private static final String TEST_EMAIL =
            "file.user@example.com";

    private static final String TEST_USERNAME =
            "file_user";

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
            0x01,
            0x00,
            0x00,
            (byte) 0xFF,
            (byte) 0xD9
    };

    private static final byte[] PNG_BYTES = {
            (byte) 0x89,
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x00,
            0x00,
            0x00,
            0x0D,
            0x49,
            0x48,
            0x44,
            0x52
    };

    private static Path uploadDirectory;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @BeforeAll
    static void createUploadDirectory() throws IOException {

        uploadDirectory =
                Files.createTempDirectory(
                        "omnia-file-upload-integration-"
                );
    }

    @DynamicPropertySource
    static void configureFileStorage(
            DynamicPropertyRegistry registry
    ) {
        /*
         * DynamicPropertySource ekzekutohet gjatë krijimit
         * të Spring context-it. Prandaj krijojmë directory-n
         * edhe këtu nëse @BeforeAll nuk është ekzekutuar ende.
         */
        if (uploadDirectory == null) {
            try {
                uploadDirectory =
                        Files.createTempDirectory(
                                "omnia-file-upload-integration-"
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
    void setUpUser() {

        uploadedFileRepository.deleteAll();

        userRepository.findByEmail(TEST_EMAIL)
                .ifPresent(userRepository::delete);

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() ->
                        new IllegalStateException(
                                "USER role is missing"
                        )
                );

        User user = User.builder()
                .firstName("File")
                .lastName("User")
                .username(TEST_USERNAME)
                .email(TEST_EMAIL)
                .passwordHash(
                        "$2a$10$integration.test.password.hash"
                )
                .role(userRole)
                .emailVerified(true)
                .build();

        userRepository.saveAndFlush(user);
    }

    @AfterEach
    void cleanUp() throws IOException {

        uploadedFileRepository.deleteAll();

        userRepository.findByEmail(TEST_EMAIL)
                .ifPresent(userRepository::delete);

        deletePhysicalFiles();
    }

    @AfterAll
    static void deleteUploadDirectory() throws IOException {

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
                                    "Could not delete test file: "
                                            + path,
                                    exception
                            );
                        }
                    });
        }
    }

    @Test
    @WithMockUser(
            username = TEST_EMAIL,
            roles = "USER"
    )
    void uploadFile_WithValidJpeg_ShouldReturnCreated()
            throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "product.jpg",
                        "image/jpeg",
                        JPEG_BYTES
                );

        mockMvc.perform(
                        multipart("/api/files/upload")
                                .file(file)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                "application/json"
                        )
                )
                .andExpect(
                        header().string(
                                "Location",
                                org.hamcrest.Matchers.matchesPattern(
                                        ".*/api/files/[0-9]+"
                                )
                        )
                )
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(
                        jsonPath("$.originalName")
                                .value("product.jpg")
                )
                .andExpect(
                        jsonPath("$.contentType")
                                .value("image/jpeg")
                )
                .andExpect(
                        jsonPath("$.size")
                                .value(JPEG_BYTES.length)
                )
                .andExpect(
                        jsonPath("$.uploadedAt").isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.url")
                                .value(
                                        org.hamcrest.Matchers
                                                .matchesPattern(
                                                        ".*/api/files/[0-9]+"
                                                )
                                )
                )
                .andExpect(
                        jsonPath("$.storedName").doesNotExist()
                )
                .andExpect(
                        jsonPath("$.checksumSha256").doesNotExist()
                )
                .andExpect(
                        jsonPath("$.path").doesNotExist()
                );

        assertThat(
                uploadedFileRepository.count()
        ).isEqualTo(1);

        assertThat(countPhysicalFiles())
                .isEqualTo(1);
    }

    @Test
    @WithMockUser(
            username = TEST_EMAIL,
            roles = "USER"
    )
    void uploadFile_WithValidPng_ShouldPersistDetectedMetadata()
            throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "avatar.PNG",
                        "image/png",
                        PNG_BYTES
                );

        mockMvc.perform(
                        multipart("/api/files/upload")
                                .file(file)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.originalName")
                                .value("avatar.PNG")
                )
                .andExpect(
                        jsonPath("$.contentType")
                                .value("image/png")
                )
                .andExpect(
                        jsonPath("$.size")
                                .value(PNG_BYTES.length)
                );

        var storedFiles =
                uploadedFileRepository.findAll();

        assertThat(storedFiles).hasSize(1);

        assertThat(
                storedFiles.getFirst().getStoredName()
        ).endsWith(".png");

        assertThat(
                storedFiles.getFirst().getChecksumSha256()
        ).matches("[0-9a-f]{64}");
    }

    @Test
    void uploadFile_WithoutAuthentication_ShouldReturnUnauthorized()
            throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "product.jpg",
                        "image/jpeg",
                        JPEG_BYTES
                );

        mockMvc.perform(
                        multipart("/api/files/upload")
                                .file(file)
                )
                .andExpect(status().isUnauthorized());
        assertThat(
                uploadedFileRepository.count()
        ).isZero();

        assertThat(countPhysicalFiles())
                .isZero();
    }

    @Test
    @WithMockUser(
            username = TEST_EMAIL,
            roles = "USER"
    )
    void uploadFile_WithEmptyFile_ShouldReturnBadRequest()
            throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "empty.jpg",
                        "image/jpeg",
                        new byte[0]
                );

        mockMvc.perform(
                        multipart("/api/files/upload")
                                .file(file)
                )
                .andExpect(status().isBadRequest());

        assertThat(
                uploadedFileRepository.count()
        ).isZero();

        assertThat(countPhysicalFiles())
                .isZero();
    }

    @Test
    @WithMockUser(
            username = TEST_EMAIL,
            roles = "USER"
    )
    void uploadFile_WithUnsupportedType_ShouldReturnBadRequest()
            throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "document.pdf",
                        "application/pdf",
                        "%PDF-1.7".getBytes()
                );

        mockMvc.perform(
                        multipart("/api/files/upload")
                                .file(file)
                )
                .andExpect(status().isBadRequest());

        assertThat(
                uploadedFileRepository.count()
        ).isZero();

        assertThat(countPhysicalFiles())
                .isZero();
    }

    @Test
    @WithMockUser(
            username = TEST_EMAIL,
            roles = "USER"
    )
    void uploadFile_WithFakeImageContent_ShouldReturnBadRequest()
            throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "fake.jpg",
                        "image/jpeg",
                        "This is not an image".getBytes()
                );

        mockMvc.perform(
                        multipart("/api/files/upload")
                                .file(file)
                )
                .andExpect(status().isBadRequest());

        assertThat(
                uploadedFileRepository.count()
        ).isZero();

        assertThat(countPhysicalFiles())
                .isZero();
    }

    @Test
    @WithMockUser(
            username = TEST_EMAIL,
            roles = "USER"
    )
    void getMetadata_WithExistingFile_ShouldReturnSafeMetadata()
            throws Exception {

        Long fileId = uploadJpegAndGetId();

        mockMvc.perform(
                        get(
                                "/api/files/{fileId}/metadata",
                                fileId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                "application/json"
                        )
                )
                .andExpect(
                        jsonPath("$.id").value(fileId)
                )
                .andExpect(
                        jsonPath("$.originalName")
                                .value("product.jpg")
                )
                .andExpect(
                        jsonPath("$.contentType")
                                .value("image/jpeg")
                )
                .andExpect(
                        jsonPath("$.size")
                                .value(JPEG_BYTES.length)
                )
                .andExpect(
                        jsonPath("$.url").isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.storedName").doesNotExist()
                )
                .andExpect(
                        jsonPath("$.checksumSha256").doesNotExist()
                )
                .andExpect(
                        jsonPath("$.path").doesNotExist()
                );
    }

    @Test
    @WithMockUser(
            username = TEST_EMAIL,
            roles = "USER"
    )
    void downloadFile_WithExistingFile_ShouldReturnContent()
            throws Exception {

        Long fileId = uploadJpegAndGetId();

        mockMvc.perform(
                        get(
                                "/api/files/{fileId}",
                                fileId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentType("image/jpeg")
                )
                .andExpect(
                        header().string(
                                "Content-Disposition",
                                org.hamcrest.Matchers.containsString(
                                        "inline"
                                )
                        )
                )
                .andExpect(
                        header().string(
                                "Content-Disposition",
                                org.hamcrest.Matchers.containsString(
                                        "product.jpg"
                                )
                        )
                )
                .andExpect(
                        header().string(
                                "X-Content-Type-Options",
                                "nosniff"
                        )
                )
                .andExpect(
                        header().string(
                                "Cache-Control",
                                org.hamcrest.Matchers.containsString(
                                        "private"
                                )
                        )
                )
                .andExpect(
                        content().bytes(JPEG_BYTES)
                );
    }

    @Test
    @WithMockUser(
            username = TEST_EMAIL,
            roles = "USER"
    )
    void getMetadata_WithMissingFile_ShouldReturnNotFound()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/files/{fileId}/metadata",
                                999999L
                        )
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(
            username = TEST_EMAIL,
            roles = "USER"
    )
    void deleteFile_AsOwner_ShouldDeleteMetadataAndPhysicalFile()
            throws Exception {

        Long fileId = uploadJpegAndGetId();

        assertThat(
                uploadedFileRepository.existsById(fileId)
        ).isTrue();

        assertThat(countPhysicalFiles())
                .isEqualTo(1);

        mockMvc.perform(
                        delete(
                                "/api/files/{fileId}",
                                fileId
                        )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(
                uploadedFileRepository.existsById(fileId)
        ).isFalse();

        assertThat(countPhysicalFiles())
                .isZero();
    }

    @Test
    @WithMockUser(
            username = TEST_EMAIL,
            roles = "USER"
    )
    void deleteFile_WhenFileDoesNotExist_ShouldReturnNotFound()
            throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/files/{fileId}",
                                999999L
                        )
                )
                .andExpect(status().isNotFound());
    }

    private Long uploadJpegAndGetId()
            throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "product.jpg",
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

    private long countPhysicalFiles()
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
}