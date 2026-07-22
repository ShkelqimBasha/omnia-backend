package com.omnia.backend.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductImageTest {

    private static final Long PRODUCT_ID = 10L;
    private static final Long FILE_ID = 20L;

    @Test
    void isUploadedFileBacked_WithUploadedFile_ShouldReturnTrue() {

        ProductImage image = ProductImage.builder()
                .uploadedFile(
                        UploadedFile.builder()
                                .id(FILE_ID)
                                .build()
                )
                .build();

        assertThat(image.isUploadedFileBacked())
                .isTrue();
    }

    @Test
    void isUploadedFileBacked_WithoutUploadedFile_ShouldReturnFalse() {

        ProductImage image = ProductImage.builder()
                .uploadedFile(null)
                .build();

        assertThat(image.isUploadedFileBacked())
                .isFalse();
    }

    @Test
    void isLegacyUrlBacked_WithNonBlankUrl_ShouldReturnTrue() {

        ProductImage image = ProductImage.builder()
                .legacyImageUrl(
                        "https://example.com/image.jpg"
                )
                .build();

        assertThat(image.isLegacyUrlBacked())
                .isTrue();
    }

    @Test
    void isLegacyUrlBacked_WithNullUrl_ShouldReturnFalse() {

        ProductImage image = ProductImage.builder()
                .legacyImageUrl(null)
                .build();

        assertThat(image.isLegacyUrlBacked())
                .isFalse();
    }

    @Test
    void isLegacyUrlBacked_WithBlankUrl_ShouldReturnFalse() {

        ProductImage image = ProductImage.builder()
                .legacyImageUrl("   ")
                .build();

        assertThat(image.isLegacyUrlBacked())
                .isFalse();
    }

    @Test
    void synchronizePrimaryProductId_WithPrimaryImage_ShouldSetProductId() {

        Product product = Product.builder()
                .id(PRODUCT_ID)
                .build();

        ProductImage image = ProductImage.builder()
                .product(product)
                .isPrimary(true)
                .primaryProductId(null)
                .build();

        invokeSynchronizePrimaryProductId(image);

        assertThat(image.getIsPrimary())
                .isTrue();

        assertThat(image.getPrimaryProductId())
                .isEqualTo(PRODUCT_ID);
    }

    @Test
    void synchronizePrimaryProductId_WithNonPrimaryImage_ShouldClearProductId() {

        Product product = Product.builder()
                .id(PRODUCT_ID)
                .build();

        ProductImage image = ProductImage.builder()
                .product(product)
                .isPrimary(false)
                .primaryProductId(PRODUCT_ID)
                .build();

        invokeSynchronizePrimaryProductId(image);

        assertThat(image.getIsPrimary())
                .isFalse();

        assertThat(image.getPrimaryProductId())
                .isNull();
    }

    @Test
    void synchronizePrimaryProductId_WithNullPrimaryFlag_ShouldNormalizeToFalse() {

        ProductImage image = ProductImage.builder()
                .product(null)
                .isPrimary(null)
                .primaryProductId(PRODUCT_ID)
                .build();

        invokeSynchronizePrimaryProductId(image);

        assertThat(image.getIsPrimary())
                .isFalse();

        assertThat(image.getPrimaryProductId())
                .isNull();
    }

    @Test
    void synchronizePrimaryProductId_WithPrimaryAndNullProduct_ShouldThrowException() {

        ProductImage image = ProductImage.builder()
                .product(null)
                .isPrimary(true)
                .build();

        assertThatThrownBy(
                () -> invokeSynchronizePrimaryProductId(
                        image
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "Primary product image requires a persisted product"
                );
    }

    @Test
    void synchronizePrimaryProductId_WithPrimaryAndTransientProduct_ShouldThrowException() {

        Product transientProduct =
                Product.builder()
                        .id(null)
                        .build();

        ProductImage image = ProductImage.builder()
                .product(transientProduct)
                .isPrimary(true)
                .build();

        assertThatThrownBy(
                () -> invokeSynchronizePrimaryProductId(
                        image
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "Primary product image requires a persisted product"
                );
    }

    private void invokeSynchronizePrimaryProductId(
            ProductImage image
    ) {

        ReflectionTestUtils.invokeMethod(
                image,
                "synchronizePrimaryProductId"
        );
    }
}