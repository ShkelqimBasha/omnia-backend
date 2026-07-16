package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.ProductImageRequest;
import com.omnia.backend.dto.response.ProductImageResponse;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductImage;
import com.omnia.backend.repository.ProductImageRepository;
import com.omnia.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private ProductImageServiceImpl imageService;

    private Product product;
    private ProductImage image;
    private ProductImageRequest request;

    @BeforeEach
    void setUp() {

        product = Product.builder()
                .id(10L)
                .name("Samsung Galaxy S24")
                .build();

        request = ProductImageRequest.builder()
                .imageUrl("https://example.com/s24-main.jpg")
                .isPrimary(true)
                .build();

        image = ProductImage.builder()
                .id(100L)
                .product(product)
                .imageUrl("https://example.com/s24-main.jpg")
                .isPrimary(true)
                .createdAt(
                        LocalDateTime.of(
                                2026,
                                7,
                                16,
                                20,
                                0
                        )
                )
                .build();
    }

    @Test
    void addImage_shouldAddImageSuccessfully() {

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(imageRepository.save(any(ProductImage.class)))
                .thenReturn(image);

        ProductImageResponse result =
                imageService.addImage(10L, request);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(10L, result.getProductId());
        assertEquals(
                "https://example.com/s24-main.jpg",
                result.getImageUrl()
        );
        assertTrue(result.getIsPrimary());
        assertEquals(
                LocalDateTime.of(2026, 7, 16, 20, 0),
                result.getCreatedAt()
        );

        ArgumentCaptor<ProductImage> imageCaptor =
                ArgumentCaptor.forClass(ProductImage.class);

        verify(imageRepository)
                .save(imageCaptor.capture());

        ProductImage savedImage =
                imageCaptor.getValue();

        assertEquals(product, savedImage.getProduct());
        assertEquals(
                "https://example.com/s24-main.jpg",
                savedImage.getImageUrl()
        );
        assertTrue(savedImage.getIsPrimary());

        verify(productRepository).findById(10L);
    }

    @Test
    void addImage_shouldSetPrimaryFalseWhenRequestValueIsNull() {

        request.setIsPrimary(null);

        ProductImage savedImage = ProductImage.builder()
                .id(101L)
                .product(product)
                .imageUrl(request.getImageUrl())
                .isPrimary(false)
                .build();

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(imageRepository.save(any(ProductImage.class)))
                .thenReturn(savedImage);

        ProductImageResponse result =
                imageService.addImage(10L, request);

        assertNotNull(result);
        assertFalse(result.getIsPrimary());

        ArgumentCaptor<ProductImage> imageCaptor =
                ArgumentCaptor.forClass(ProductImage.class);

        verify(imageRepository)
                .save(imageCaptor.capture());

        assertFalse(imageCaptor.getValue().getIsPrimary());
    }

    @Test
    void addImage_shouldThrowWhenProductDoesNotExist() {

        when(productRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> imageService.addImage(99L, request)
                );

        assertEquals(
                "Product not found",
                exception.getMessage()
        );

        verify(imageRepository, never())
                .save(any(ProductImage.class));
    }

    @Test
    void getProductImages_shouldReturnImagesInRepositoryOrder() {

        ProductImage secondImage = ProductImage.builder()
                .id(101L)
                .product(product)
                .imageUrl("https://example.com/s24-side.jpg")
                .isPrimary(false)
                .createdAt(
                        LocalDateTime.of(
                                2026,
                                7,
                                16,
                                20,
                                5
                        )
                )
                .build();

        when(imageRepository
                .findByProductIdOrderByIsPrimaryDescIdAsc(10L))
                .thenReturn(List.of(image, secondImage));

        List<ProductImageResponse> result =
                imageService.getProductImages(10L);

        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals(
                "https://example.com/s24-main.jpg",
                result.get(0).getImageUrl()
        );

        assertTrue(result.get(0).getIsPrimary());

        assertEquals(
                "https://example.com/s24-side.jpg",
                result.get(1).getImageUrl()
        );

        assertFalse(result.get(1).getIsPrimary());

        verify(imageRepository)
                .findByProductIdOrderByIsPrimaryDescIdAsc(10L);
    }

    @Test
    void getProductImages_shouldReturnEmptyList() {

        when(imageRepository
                .findByProductIdOrderByIsPrimaryDescIdAsc(10L))
                .thenReturn(List.of());

        List<ProductImageResponse> result =
                imageService.getProductImages(10L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void updateImage_shouldUpdateImageSuccessfully() {

        ProductImageRequest updateRequest =
                ProductImageRequest.builder()
                        .imageUrl(
                                "https://example.com/s24-updated.jpg"
                        )
                        .isPrimary(false)
                        .build();

        when(imageRepository.findById(100L))
                .thenReturn(Optional.of(image));

        when(imageRepository.save(image))
                .thenReturn(image);

        ProductImageResponse result =
                imageService.updateImage(
                        100L,
                        updateRequest
                );

        assertNotNull(result);
        assertEquals(
                "https://example.com/s24-updated.jpg",
                result.getImageUrl()
        );
        assertFalse(result.getIsPrimary());

        assertEquals(
                "https://example.com/s24-updated.jpg",
                image.getImageUrl()
        );
        assertFalse(image.getIsPrimary());

        verify(imageRepository).save(image);
    }

    @Test
    void updateImage_shouldSetPrimaryFalseWhenRequestValueIsNull() {

        ProductImageRequest updateRequest =
                ProductImageRequest.builder()
                        .imageUrl(
                                "https://example.com/s24-updated.jpg"
                        )
                        .isPrimary(null)
                        .build();

        when(imageRepository.findById(100L))
                .thenReturn(Optional.of(image));

        when(imageRepository.save(image))
                .thenReturn(image);

        ProductImageResponse result =
                imageService.updateImage(
                        100L,
                        updateRequest
                );

        assertNotNull(result);
        assertFalse(result.getIsPrimary());
        assertFalse(image.getIsPrimary());
    }

    @Test
    void updateImage_shouldThrowWhenImageDoesNotExist() {

        when(imageRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> imageService.updateImage(
                                99L,
                                request
                        )
                );

        assertEquals(
                "Image not found",
                exception.getMessage()
        );

        verify(imageRepository, never())
                .save(any(ProductImage.class));
    }

    @Test
    void deleteImage_shouldDeleteImageSuccessfully() {

        when(imageRepository.findById(100L))
                .thenReturn(Optional.of(image));

        imageService.deleteImage(100L);

        verify(imageRepository)
                .delete(image);
    }

    @Test
    void deleteImage_shouldThrowWhenImageDoesNotExist() {

        when(imageRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> imageService.deleteImage(99L)
                );

        assertEquals(
                "Image not found",
                exception.getMessage()
        );

        verify(imageRepository, never())
                .delete(any(ProductImage.class));
    }
}