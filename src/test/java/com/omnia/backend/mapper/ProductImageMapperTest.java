package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.ProductImageResponse;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductImage;
import com.omnia.backend.entity.UploadedFile;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductImageMapperTest {

    private static final Long PRODUCT_ID = 10L;
    private static final Long IMAGE_ID = 20L;
    private static final Long UPLOADED_FILE_ID = 30L;

    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(
                    2026,
                    7,
                    22,
                    12,
                    30
            );

    @Test
    void toResponse_WithUploadedFile_ShouldResolveFileEndpoint() {

        UploadedFile uploadedFile =
                UploadedFile.builder()
                        .id(UPLOADED_FILE_ID)
                        .build();

        ProductImage image =
                ProductImage.builder()
                        .id(IMAGE_ID)
                        .product(createProduct())
                        .uploadedFile(uploadedFile)
                        .legacyImageUrl(null)
                        .isPrimary(true)
                        .createdAt(CREATED_AT)
                        .build();

        ProductImageResponse response =
                ProductImageMapper.toResponse(image);

        assertThat(response)
                .isNotNull();

        assertThat(response.getId())
                .isEqualTo(IMAGE_ID);

        assertThat(response.getProductId())
                .isEqualTo(PRODUCT_ID);

        assertThat(response.getUploadedFileId())
                .isEqualTo(UPLOADED_FILE_ID);

        assertThat(response.getImageUrl())
                .isEqualTo(
                        "/api/files/" + UPLOADED_FILE_ID
                );

        assertThat(response.getIsPrimary())
                .isTrue();

        assertThat(response.getCreatedAt())
                .isEqualTo(CREATED_AT);
    }

    @Test
    void toResponse_WithLegacyUrl_ShouldResolveLegacyUrl() {

        ProductImage image =
                ProductImage.builder()
                        .id(IMAGE_ID)
                        .product(createProduct())
                        .uploadedFile(null)
                        .legacyImageUrl(
                                "https://example.com/legacy-image.jpg"
                        )
                        .isPrimary(false)
                        .createdAt(CREATED_AT)
                        .build();

        ProductImageResponse response =
                ProductImageMapper.toResponse(image);

        assertThat(response)
                .isNotNull();

        assertThat(response.getId())
                .isEqualTo(IMAGE_ID);

        assertThat(response.getProductId())
                .isEqualTo(PRODUCT_ID);

        assertThat(response.getUploadedFileId())
                .isNull();

        assertThat(response.getImageUrl())
                .isEqualTo(
                        "https://example.com/legacy-image.jpg"
                );

        assertThat(response.getIsPrimary())
                .isFalse();

        assertThat(response.getCreatedAt())
                .isEqualTo(CREATED_AT);
    }

    @Test
    void toResponse_WithNullPrimaryFlag_ShouldMapPrimaryAsFalse() {

        ProductImage image =
                ProductImage.builder()
                        .id(IMAGE_ID)
                        .product(createProduct())
                        .uploadedFile(
                                UploadedFile.builder()
                                        .id(UPLOADED_FILE_ID)
                                        .build()
                        )
                        .legacyImageUrl(null)
                        .isPrimary(null)
                        .createdAt(CREATED_AT)
                        .build();

        ProductImageResponse response =
                ProductImageMapper.toResponse(image);

        assertThat(response.getIsPrimary())
                .isFalse();

        assertThat(response.getImageUrl())
                .isEqualTo(
                        "/api/files/" + UPLOADED_FILE_ID
                );
    }

    @Test
    void toResponse_WithoutValidImageSource_ShouldThrowException() {

        ProductImage image =
                ProductImage.builder()
                        .id(IMAGE_ID)
                        .product(createProduct())
                        .uploadedFile(null)
                        .legacyImageUrl("   ")
                        .isPrimary(false)
                        .createdAt(CREATED_AT)
                        .build();

        assertThatThrownBy(
                () -> ProductImageMapper.toResponse(image)
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "Product image has no valid image source"
                );
    }

    private Product createProduct() {

        return Product.builder()
                .id(PRODUCT_ID)
                .build();
    }
}