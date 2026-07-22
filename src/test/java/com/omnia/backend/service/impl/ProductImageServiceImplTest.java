package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.ProductImageRequest;
import com.omnia.backend.dto.response.ProductImageResponse;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductImage;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.repository.ProductImageRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.UploadedFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository imageRepository;

    @Mock
    private UploadedFileRepository uploadedFileRepository;

    private ProductImageServiceImpl productImageService;

    private Product product;
    private UploadedFile uploadedFile;
    private ProductImageRequest request;

    @BeforeEach
    void setUp() {

        productImageService =
                new ProductImageServiceImpl(
                        productRepository,
                        imageRepository,
                        uploadedFileRepository
                );

        product = Product.builder()
                .id(1L)
                .name("Test product")
                .build();

        uploadedFile = UploadedFile.builder()
                .id(10L)
                .originalName("product.jpg")
                .storedName("stored-product.jpg")
                .contentType("image/jpeg")
                .size(100L)
                .checksumSha256("0".repeat(64))
                .uploadedAt(LocalDateTime.now())
                .build();

        request = ProductImageRequest.builder()
                .uploadedFileId(10L)
                .isPrimary(false)
                .build();
    }

    @Test
    void addImage_WithValidRequest_ShouldCreateImage() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        when(imageRepository.existsByUploadedFileId(10L))
                .thenReturn(false);

        when(imageRepository.saveAndFlush(
                any(ProductImage.class)
        )).thenAnswer(invocation -> {

            ProductImage image =
                    invocation.getArgument(0);

            image.setId(100L);
            image.setCreatedAt(
                    LocalDateTime.of(
                            2026,
                            7,
                            22,
                            14,
                            0
                    )
            );

            return image;
        });

        ProductImageResponse response =
                productImageService.addImage(
                        1L,
                        request
                );

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(1L, response.getProductId());
        assertEquals(10L, response.getUploadedFileId());
        assertEquals(
                "/api/files/10",
                response.getImageUrl()
        );
        assertFalse(response.getIsPrimary());

        ArgumentCaptor<ProductImage> captor =
                ArgumentCaptor.forClass(
                        ProductImage.class
                );

        verify(imageRepository)
                .saveAndFlush(captor.capture());

        ProductImage savedImage =
                captor.getValue();

        assertSame(
                product,
                savedImage.getProduct()
        );
        assertSame(
                uploadedFile,
                savedImage.getUploadedFile()
        );
        assertNull(savedImage.getLegacyImageUrl());
        assertFalse(savedImage.getIsPrimary());

        verify(
                imageRepository,
                never()
        ).findPrimaryForUpdate(anyLong());
    }

    @Test
    void addImage_AsPrimary_ShouldClearExistingPrimary() {

        request.setIsPrimary(true);

        UploadedFile oldUploadedFile =
                createUploadedFile(
                        11L,
                        "old.jpg"
                );

        ProductImage existingPrimary =
                createProductImage(
                        50L,
                        product,
                        oldUploadedFile,
                        true
                );

        existingPrimary.setPrimaryProductId(
                product.getId()
        );

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        when(imageRepository.existsByUploadedFileId(10L))
                .thenReturn(false);

        when(imageRepository.findPrimaryForUpdate(1L))
                .thenReturn(
                        Optional.of(existingPrimary)
                );

        when(imageRepository.saveAndFlush(
                any(ProductImage.class)
        )).thenAnswer(invocation -> {

            ProductImage image =
                    invocation.getArgument(0);

            if (image.getId() == null) {
                image.setId(100L);
            }

            return image;
        });

        ProductImageResponse response =
                productImageService.addImage(
                        1L,
                        request
                );

        assertTrue(response.getIsPrimary());

        assertFalse(
                existingPrimary.getIsPrimary()
        );
        assertNull(
                existingPrimary.getPrimaryProductId()
        );

        verify(imageRepository)
                .findPrimaryForUpdate(1L);

        verify(imageRepository, times(2))
                .saveAndFlush(
                        any(ProductImage.class)
                );
    }

    @Test
    void addImage_AsFirstPrimary_ShouldCreatePrimaryImage() {

        request.setIsPrimary(true);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        when(imageRepository.existsByUploadedFileId(10L))
                .thenReturn(false);

        when(imageRepository.findPrimaryForUpdate(1L))
                .thenReturn(Optional.empty());

        when(imageRepository.saveAndFlush(
                any(ProductImage.class)
        )).thenAnswer(invocation -> {

            ProductImage image =
                    invocation.getArgument(0);

            image.setId(100L);

            return image;
        });

        ProductImageResponse response =
                productImageService.addImage(
                        1L,
                        request
                );

        assertTrue(response.getIsPrimary());

        verify(imageRepository)
                .findPrimaryForUpdate(1L);
    }

    @Test
    void addImage_WhenProductDoesNotExist_ShouldThrowException() {

        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> productImageService.addImage(
                                999L,
                                request
                        )
                );

        assertEquals(
                "Product not found",
                exception.getMessage()
        );

        verifyNoInteractions(
                uploadedFileRepository
        );

        verify(imageRepository, never())
                .saveAndFlush(
                        any(ProductImage.class)
                );
    }

    @Test
    void addImage_WhenUploadedFileDoesNotExist_ShouldThrowException() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> productImageService.addImage(
                                1L,
                                request
                        )
                );

        assertEquals(
                "Uploaded file not found",
                exception.getMessage()
        );

        verify(imageRepository, never())
                .saveAndFlush(
                        any(ProductImage.class)
                );
    }

    @Test
    void addImage_WhenFileIsAlreadyAttached_ShouldThrowException() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        when(imageRepository.existsByUploadedFileId(10L))
                .thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> productImageService.addImage(
                                1L,
                                request
                        )
                );

        assertEquals(
                "Uploaded file is already attached "
                        + "to a product image",
                exception.getMessage()
        );

        verify(imageRepository, never())
                .saveAndFlush(
                        any(ProductImage.class)
                );
    }

    @Test
    void addImage_WithInvalidProductId_ShouldThrowException() {

        assertThrows(
                IllegalArgumentException.class,
                () -> productImageService.addImage(
                        0L,
                        request
                )
        );

        verifyNoInteractions(
                productRepository,
                uploadedFileRepository,
                imageRepository
        );
    }

    @Test
    void addImage_WithInvalidUploadedFileId_ShouldThrowException() {

        request.setUploadedFileId(0L);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        assertThrows(
                IllegalArgumentException.class,
                () -> productImageService.addImage(
                        1L,
                        request
                )
        );

        verifyNoInteractions(
                uploadedFileRepository
        );

        verify(imageRepository, never())
                .saveAndFlush(
                        any(ProductImage.class)
                );
    }

    @Test
    void getProductImages_ShouldReturnUploadedAndLegacyImages() {

        UploadedFile secondFile =
                createUploadedFile(
                        11L,
                        "second.png"
                );

        ProductImage uploadedImage =
                createProductImage(
                        100L,
                        product,
                        uploadedFile,
                        true
                );

        ProductImage secondUploadedImage =
                createProductImage(
                        101L,
                        product,
                        secondFile,
                        false
                );

        ProductImage legacyImage =
                ProductImage.builder()
                        .id(102L)
                        .product(product)
                        .legacyImageUrl(
                                "https://legacy.example/image.jpg"
                        )
                        .isPrimary(false)
                        .createdAt(LocalDateTime.now())
                        .build();

        when(productRepository.existsById(1L))
                .thenReturn(true);

        when(imageRepository
                .findByProductIdOrderByIsPrimaryDescIdAsc(
                        1L
                ))
                .thenReturn(
                        List.of(
                                uploadedImage,
                                secondUploadedImage,
                                legacyImage
                        )
                );

        List<ProductImageResponse> responses =
                productImageService
                        .getProductImages(1L);

        assertEquals(3, responses.size());

        assertEquals(
                "/api/files/10",
                responses.get(0).getImageUrl()
        );
        assertEquals(
                "/api/files/11",
                responses.get(1).getImageUrl()
        );
        assertEquals(
                "https://legacy.example/image.jpg",
                responses.get(2).getImageUrl()
        );
    }

    @Test
    void getProductImages_WhenProductDoesNotExist_ShouldThrowException() {

        when(productRepository.existsById(999L))
                .thenReturn(false);

        assertThrows(
                ResourceNotFoundException.class,
                () -> productImageService
                        .getProductImages(999L)
        );

        verify(imageRepository, never())
                .findByProductIdOrderByIsPrimaryDescIdAsc(
                        anyLong()
                );
    }

    @Test
    void getProductImages_WithInvalidProductId_ShouldThrowException() {

        assertThrows(
                IllegalArgumentException.class,
                () -> productImageService
                        .getProductImages(-1L)
        );

        verifyNoInteractions(
                productRepository,
                imageRepository
        );
    }

    @Test
    void updateImage_WithValidRequest_ShouldReplaceUploadedFile() {

        UploadedFile oldFile =
                createUploadedFile(
                        11L,
                        "old.jpg"
                );

        ProductImage image =
                createProductImage(
                        100L,
                        product,
                        oldFile,
                        false
                );

        when(imageRepository.findById(100L))
                .thenReturn(Optional.of(image));

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        when(imageRepository
                .existsByUploadedFileIdAndIdNot(
                        10L,
                        100L
                ))
                .thenReturn(false);

        when(imageRepository.saveAndFlush(image))
                .thenReturn(image);

        ProductImageResponse response =
                productImageService.updateImage(
                        100L,
                        request
                );

        assertSame(
                uploadedFile,
                image.getUploadedFile()
        );
        assertNull(image.getLegacyImageUrl());
        assertFalse(image.getIsPrimary());

        assertEquals(
                10L,
                response.getUploadedFileId()
        );
        assertEquals(
                "/api/files/10",
                response.getImageUrl()
        );
    }

    @Test
    void updateImage_ToPrimary_ShouldClearOtherPrimary() {

        request.setIsPrimary(true);

        ProductImage image =
                createProductImage(
                        100L,
                        product,
                        uploadedFile,
                        false
                );

        UploadedFile existingFile =
                createUploadedFile(
                        11L,
                        "existing.jpg"
                );

        ProductImage existingPrimary =
                createProductImage(
                        101L,
                        product,
                        existingFile,
                        true
                );

        existingPrimary.setPrimaryProductId(1L);

        when(imageRepository.findById(100L))
                .thenReturn(Optional.of(image));

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        when(imageRepository
                .existsByUploadedFileIdAndIdNot(
                        10L,
                        100L
                ))
                .thenReturn(false);

        when(imageRepository.findPrimaryForUpdate(1L))
                .thenReturn(
                        Optional.of(existingPrimary)
                );

        when(imageRepository.saveAndFlush(
                any(ProductImage.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        ProductImageResponse response =
                productImageService.updateImage(
                        100L,
                        request
                );

        assertTrue(response.getIsPrimary());

        assertFalse(
                existingPrimary.getIsPrimary()
        );
        assertNull(
                existingPrimary.getPrimaryProductId()
        );

        verify(imageRepository, times(2))
                .saveAndFlush(
                        any(ProductImage.class)
                );
    }

    @Test
    void updateImage_WhenImageDoesNotExist_ShouldThrowException() {

        when(imageRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> productImageService.updateImage(
                        999L,
                        request
                )
        );

        verifyNoInteractions(
                uploadedFileRepository
        );
    }

    @Test
    void updateImage_WhenUploadedFileIsUsedByAnotherImage_ShouldThrow() {

        ProductImage image =
                createProductImage(
                        100L,
                        product,
                        createUploadedFile(
                                11L,
                                "old.jpg"
                        ),
                        false
                );

        when(imageRepository.findById(100L))
                .thenReturn(Optional.of(image));

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        when(imageRepository
                .existsByUploadedFileIdAndIdNot(
                        10L,
                        100L
                ))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> productImageService.updateImage(
                        100L,
                        request
                )
        );

        verify(imageRepository, never())
                .saveAndFlush(
                        any(ProductImage.class)
                );
    }

    @Test
    void updateImage_WithInvalidImageId_ShouldThrowException() {

        assertThrows(
                IllegalArgumentException.class,
                () -> productImageService.updateImage(
                        0L,
                        request
                )
        );

        verifyNoInteractions(
                imageRepository,
                uploadedFileRepository
        );
    }

    @Test
    void deleteImage_WithExistingImage_ShouldDeleteRelationship() {

        ProductImage image =
                createProductImage(
                        100L,
                        product,
                        uploadedFile,
                        false
                );

        when(imageRepository.findById(100L))
                .thenReturn(Optional.of(image));

        productImageService.deleteImage(100L);

        verify(imageRepository)
                .delete(image);

        verify(imageRepository)
                .flush();

        /*
         * ProductImage deletion must not automatically delete
         * UploadedFile metadata or the physical file.
         */
        verifyNoInteractions(
                uploadedFileRepository
        );
    }

    @Test
    void deleteImage_WhenImageDoesNotExist_ShouldThrowException() {

        when(imageRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> productImageService
                        .deleteImage(999L)
        );

        verify(imageRepository, never())
                .delete(any(ProductImage.class));

        verify(imageRepository, never())
                .flush();
    }

    @Test
    void deleteImage_WithInvalidId_ShouldThrowException() {

        assertThrows(
                IllegalArgumentException.class,
                () -> productImageService
                        .deleteImage(-1L)
        );

        verifyNoInteractions(imageRepository);
    }

    private UploadedFile createUploadedFile(
            Long id,
            String storedName
    ) {
        return UploadedFile.builder()
                .id(id)
                .originalName(storedName)
                .storedName(storedName)
                .contentType("image/jpeg")
                .size(100L)
                .checksumSha256("0".repeat(64))
                .uploadedAt(LocalDateTime.now())
                .build();
    }

    private ProductImage createProductImage(
            Long id,
            Product imageProduct,
            UploadedFile file,
            boolean primary
    ) {
        return ProductImage.builder()
                .id(id)
                .product(imageProduct)
                .uploadedFile(file)
                .legacyImageUrl(null)
                .isPrimary(primary)
                .primaryProductId(
                        primary
                                ? imageProduct.getId()
                                : null
                )
                .createdAt(LocalDateTime.now())
                .build();
    }
}