package com.omnia.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnia.backend.dto.request.ProductImageRequest;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.ProductImageRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UploadedFileRepository;
import com.omnia.backend.repository.UserRepository;
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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockUser(
        username = "product.image.admin@example.com",
        roles = "ADMIN"
)
class ProductImageControllerIntegrationTest
        extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL =
            "product.image.admin@example.com";

    private static final String ADMIN_USERNAME =
            "product_image_admin";

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
    private ProductImageRepository productImageRepository;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Product product;

    @DynamicPropertySource
    static void configureFileStorage(
            DynamicPropertyRegistry registry
    ) {
        if (uploadDirectory == null) {
            try {
                uploadDirectory =
                        Files.createTempDirectory(
                                "omnia-product-image-integration-"
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

        Role adminRole = roleRepository
                .findByName("ADMIN")
                .orElseThrow(() ->
                        new IllegalStateException(
                                "ADMIN role is missing"
                        )
                );

        User admin = User.builder()
                .firstName("Product")
                .lastName("Image Admin")
                .username(ADMIN_USERNAME)
                .email(ADMIN_EMAIL)
                .passwordHash(
                        "$2a$10$integration.test.password.hash"
                )
                .role(adminRole)
                .emailVerified(true)
                .build();

        userRepository.saveAndFlush(admin);

        Category category = Category.builder()
                .name("Product image category")
                .description(
                        "Category used by product image tests"
                )
                .imageUrl(
                        "https://example.com/category.jpg"
                )
                .status(CategoryStatus.ACTIVE)
                .build();

        category = categoryRepository
                .saveAndFlush(category);

        product = Product.builder()
                .name("Product with images")
                .description(
                        "Product used by image integration tests"
                )
                .brand("Omnia")
                .price(new BigDecimal("499.99"))
                .discountPrice(
                        new BigDecimal("449.99")
                )
                .stock(10)
                .category(category)
                .status(ProductStatus.ACTIVE)
                .build();

        product = productRepository
                .saveAndFlush(product);
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
    void uploadAndAttachImage_ShouldCompleteFullFlow()
            throws Exception {

        Long uploadedFileId = uploadJpeg(
                "product-main.jpg"
        );

        ProductImageRequest request =
                createRequest(
                        uploadedFileId,
                        true
                );

        MvcResult result = mockMvc.perform(
                        post(
                                "/api/product-images/product/{productId}",
                                product.getId()
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
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                org.hamcrest.Matchers.matchesPattern(
                                        ".*/api/product-images/[0-9]+"
                                )
                        )
                )
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(
                        jsonPath("$.productId")
                                .value(product.getId())
                )
                .andExpect(
                        jsonPath("$.uploadedFileId")
                                .value(uploadedFileId)
                )
                .andExpect(
                        jsonPath("$.imageUrl")
                                .value(
                                        "/api/files/"
                                                + uploadedFileId
                                )
                )
                .andExpect(
                        jsonPath("$.isPrimary")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.createdAt")
                                .isNotEmpty()
                )
                .andReturn();

        Long productImageId =
                readId(result);

        assertThat(
                productImageRepository
                        .existsById(productImageId)
        ).isTrue();

        mockMvc.perform(
                        get(
                                "/api/product-images/product/{productId}",
                                product.getId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(
                        jsonPath("$.length()").value(1)
                )
                .andExpect(
                        jsonPath("$[0].id")
                                .value(productImageId)
                )
                .andExpect(
                        jsonPath("$[0].uploadedFileId")
                                .value(uploadedFileId)
                )
                .andExpect(
                        jsonPath("$[0].imageUrl")
                                .value(
                                        "/api/files/"
                                                + uploadedFileId
                                )
                )
                .andExpect(
                        jsonPath("$[0].isPrimary")
                                .value(true)
                );

        mockMvc.perform(
                        get(
                                "/api/files/{fileId}",
                                uploadedFileId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentType("image/jpeg")
                )
                .andExpect(
                        content().bytes(JPEG_BYTES)
                );
    }

    @Test
    void addSecondPrimary_ShouldReplaceExistingPrimary()
            throws Exception {

        Long firstFileId = uploadJpeg(
                "first.jpg"
        );

        Long firstImageId = attachImage(
                firstFileId,
                true
        );

        Long secondFileId = uploadPng(
                "second.png"
        );

        Long secondImageId = attachImage(
                secondFileId,
                true
        );

        mockMvc.perform(
                        get(
                                "/api/product-images/product/{productId}",
                                product.getId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(
                        jsonPath("$[0].id")
                                .value(secondImageId)
                )
                .andExpect(
                        jsonPath("$[0].isPrimary")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(firstImageId)
                )
                .andExpect(
                        jsonPath("$[1].isPrimary")
                                .value(false)
                );

        long primaryCount =
                productImageRepository.findAll()
                        .stream()
                        .filter(image ->
                                Boolean.TRUE.equals(
                                        image.getIsPrimary()
                                )
                        )
                        .count();

        assertThat(primaryCount).isEqualTo(1);
    }

    @Test
    void attachSameUploadedFileTwice_ShouldReturnBadRequest()
            throws Exception {

        Long uploadedFileId = uploadJpeg(
                "duplicate.jpg"
        );

        attachImage(
                uploadedFileId,
                false
        );

        ProductImageRequest secondRequest =
                createRequest(
                        uploadedFileId,
                        false
                );

        mockMvc.perform(
                        post(
                                "/api/product-images/product/{productId}",
                                product.getId()
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                secondRequest
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message").value(
                                "Uploaded file is already attached "
                                        + "to a product image"
                        )
                );

        assertThat(
                productImageRepository.count()
        ).isEqualTo(1);
    }

    @Test
    void attachMissingUploadedFile_ShouldReturnNotFound()
            throws Exception {

        ProductImageRequest request =
                createRequest(
                        999999L,
                        false
                );

        mockMvc.perform(
                        post(
                                "/api/product-images/product/{productId}",
                                product.getId()
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
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.message").value(
                                "Uploaded file not found"
                        )
                );
    }

    @Test
    void attachImage_WithNullFileId_ShouldReturnBadRequest()
            throws Exception {

        ProductImageRequest request =
                ProductImageRequest.builder()
                        .uploadedFileId(null)
                        .isPrimary(false)
                        .build();

        mockMvc.perform(
                        post(
                                "/api/product-images/product/{productId}",
                                product.getId()
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
                .andExpect(status().isBadRequest());

        assertThat(
                productImageRepository.count()
        ).isZero();
    }

    @Test
    @WithMockUser(
            username = "regular.user@example.com",
            roles = "USER"
    )
    void attachImage_AsRegularUser_ShouldReturnForbidden()
            throws Exception {

        ProductImageRequest request =
                createRequest(
                        999999L,
                        false
                );

        mockMvc.perform(
                        post(
                                "/api/product-images/product/{productId}",
                                product.getId()
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

        assertThat(
                productImageRepository.count()
        ).isZero();
    }

    @Test
    void updateImage_ShouldReplaceFileAndPrimaryStatus()
            throws Exception {

        Long firstFileId = uploadJpeg(
                "first.jpg"
        );

        Long productImageId = attachImage(
                firstFileId,
                false
        );

        Long replacementFileId = uploadPng(
                "replacement.png"
        );

        ProductImageRequest request =
                createRequest(
                        replacementFileId,
                        true
                );

        mockMvc.perform(
                        put(
                                "/api/product-images/{imageId}",
                                productImageId
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
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(productImageId)
                )
                .andExpect(
                        jsonPath("$.uploadedFileId")
                                .value(replacementFileId)
                )
                .andExpect(
                        jsonPath("$.imageUrl")
                                .value(
                                        "/api/files/"
                                                + replacementFileId
                                )
                )
                .andExpect(
                        jsonPath("$.isPrimary")
                                .value(true)
                );

        assertThat(
                uploadedFileRepository
                        .existsById(firstFileId)
        ).isTrue();

        assertThat(
                uploadedFileRepository
                        .existsById(replacementFileId)
        ).isTrue();
    }

    @Test
    void deleteImage_ShouldDeleteRelationButRetainUploadedFile()
            throws Exception {

        Long uploadedFileId = uploadJpeg(
                "retained.jpg"
        );

        Long productImageId = attachImage(
                uploadedFileId,
                false
        );

        assertThat(countPhysicalFiles())
                .isEqualTo(1);

        mockMvc.perform(
                        delete(
                                "/api/product-images/{imageId}",
                                productImageId
                        )
                )
                .andExpect(status().isNoContent());

        assertThat(
                productImageRepository
                        .existsById(productImageId)
        ).isFalse();

        assertThat(
                uploadedFileRepository
                        .existsById(uploadedFileId)
        ).isTrue();

        assertThat(countPhysicalFiles())
                .isEqualTo(1);
    }

    @Test
    void getImages_WhenProductDoesNotExist_ShouldReturnNotFound()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/product-images/product/{productId}",
                                999999L
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.message")
                                .value("Product not found")
                );
    }

    private Long uploadJpeg(String filename)
            throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        filename,
                        "image/jpeg",
                        JPEG_BYTES
                );

        return uploadAndReadId(file);
    }

    private Long uploadPng(String filename)
            throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        filename,
                        "image/png",
                        PNG_BYTES
                );

        return uploadAndReadId(file);
    }

    private Long uploadAndReadId(
            MockMultipartFile file
    ) throws Exception {

        MvcResult result = mockMvc.perform(
                        multipart("/api/files/upload")
                                .file(file)
                )
                .andExpect(status().isCreated())
                .andReturn();

        return readId(result);
    }

    private Long attachImage(
            Long uploadedFileId,
            boolean primary
    ) throws Exception {

        ProductImageRequest request =
                createRequest(
                        uploadedFileId,
                        primary
                );

        MvcResult result = mockMvc.perform(
                        post(
                                "/api/product-images/product/{productId}",
                                product.getId()
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
                .andExpect(status().isCreated())
                .andReturn();

        return readId(result);
    }

    private ProductImageRequest createRequest(
            Long uploadedFileId,
            boolean primary
    ) {
        return ProductImageRequest.builder()
                .uploadedFileId(uploadedFileId)
                .isPrimary(primary)
                .build();
    }

    private Long readId(MvcResult result)
            throws Exception {

        JsonNode response =
                objectMapper.readTree(
                        result.getResponse()
                                .getContentAsString()
                );

        return response.get("id").asLong();
    }

    private void cleanDatabase() {

        productImageRepository.deleteAll();
        uploadedFileRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        userRepository.findByEmail(ADMIN_EMAIL)
                .ifPresent(userRepository::delete);
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