package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.ProductVariantRequest;
import com.omnia.backend.dto.response.ProductVariantResponse;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductVariant;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @InjectMocks
    private ProductVariantServiceImpl variantService;

    private Product product;
    private ProductVariant variant;
    private ProductVariantRequest request;

    @BeforeEach
    void setUp() {

        product = Product.builder()
                .id(10L)
                .name("Samsung Galaxy S24")
                .build();

        request = ProductVariantRequest.builder()
                .variantName("Color")
                .variantValue("Black")
                .priceAdjustment(new BigDecimal("50.00"))
                .stock(15)
                .build();

        variant = ProductVariant.builder()
                .id(100L)
                .product(product)
                .variantName("Color")
                .variantValue("Black")
                .priceAdjustment(new BigDecimal("50.00"))
                .stock(15)
                .build();
    }

    @Test
    void addVariant_shouldAddVariantSuccessfully() {

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(variantRepository.save(any(ProductVariant.class)))
                .thenReturn(variant);

        ProductVariantResponse result =
                variantService.addVariant(10L, request);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(10L, result.getProductId());
        assertEquals("Color", result.getVariantName());
        assertEquals("Black", result.getVariantValue());
        assertEquals(
                new BigDecimal("50.00"),
                result.getPriceAdjustment()
        );
        assertEquals(15, result.getStock());

        ArgumentCaptor<ProductVariant> variantCaptor =
                ArgumentCaptor.forClass(ProductVariant.class);

        verify(variantRepository)
                .save(variantCaptor.capture());

        ProductVariant savedVariant =
                variantCaptor.getValue();

        assertEquals(product, savedVariant.getProduct());
        assertEquals("Color", savedVariant.getVariantName());
        assertEquals("Black", savedVariant.getVariantValue());
        assertEquals(
                new BigDecimal("50.00"),
                savedVariant.getPriceAdjustment()
        );
        assertEquals(15, savedVariant.getStock());

        verify(productRepository).findById(10L);
    }

    @Test
    void addVariant_shouldThrowWhenProductDoesNotExist() {

        when(productRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> variantService.addVariant(99L, request)
                );

        assertEquals(
                "Product not found",
                exception.getMessage()
        );

        verify(variantRepository, never())
                .save(any(ProductVariant.class));
    }

    @Test
    void getProductVariants_shouldReturnVariants() {

        ProductVariant secondVariant =
                ProductVariant.builder()
                        .id(101L)
                        .product(product)
                        .variantName("Storage")
                        .variantValue("256GB")
                        .priceAdjustment(new BigDecimal("100.00"))
                        .stock(8)
                        .build();

        when(variantRepository.findByProductId(10L))
                .thenReturn(List.of(variant, secondVariant));

        List<ProductVariantResponse> result =
                variantService.getProductVariants(10L);

        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals("Color", result.get(0).getVariantName());
        assertEquals("Black", result.get(0).getVariantValue());

        assertEquals("Storage", result.get(1).getVariantName());
        assertEquals("256GB", result.get(1).getVariantValue());

        verify(variantRepository)
                .findByProductId(10L);
    }

    @Test
    void getProductVariants_shouldReturnEmptyList() {

        when(variantRepository.findByProductId(10L))
                .thenReturn(List.of());

        List<ProductVariantResponse> result =
                variantService.getProductVariants(10L);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(variantRepository)
                .findByProductId(10L);
    }

    @Test
    void updateVariant_shouldUpdateVariantSuccessfully() {

        ProductVariantRequest updateRequest =
                ProductVariantRequest.builder()
                        .variantName("Storage")
                        .variantValue("512GB")
                        .priceAdjustment(new BigDecimal("200.00"))
                        .stock(6)
                        .build();

        when(variantRepository.findById(100L))
                .thenReturn(Optional.of(variant));

        when(variantRepository.save(variant))
                .thenReturn(variant);

        ProductVariantResponse result =
                variantService.updateVariant(
                        100L,
                        updateRequest
                );

        assertNotNull(result);
        assertEquals("Storage", result.getVariantName());
        assertEquals("512GB", result.getVariantValue());
        assertEquals(
                new BigDecimal("200.00"),
                result.getPriceAdjustment()
        );
        assertEquals(6, result.getStock());

        assertEquals("Storage", variant.getVariantName());
        assertEquals("512GB", variant.getVariantValue());
        assertEquals(
                new BigDecimal("200.00"),
                variant.getPriceAdjustment()
        );
        assertEquals(6, variant.getStock());

        verify(variantRepository).save(variant);
    }

    @Test
    void updateVariant_shouldThrowWhenVariantDoesNotExist() {

        when(variantRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> variantService.updateVariant(
                                99L,
                                request
                        )
                );

        assertEquals(
                "Variant not found",
                exception.getMessage()
        );

        verify(variantRepository, never())
                .save(any(ProductVariant.class));
    }

    @Test
    void deleteVariant_shouldDeleteVariantSuccessfully() {

        when(variantRepository.findById(100L))
                .thenReturn(Optional.of(variant));

        variantService.deleteVariant(100L);

        verify(variantRepository)
                .delete(variant);
    }

    @Test
    void deleteVariant_shouldThrowWhenVariantDoesNotExist() {

        when(variantRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> variantService.deleteVariant(99L)
                );

        assertEquals(
                "Variant not found",
                exception.getMessage()
        );

        verify(variantRepository, never())
                .delete(any(ProductVariant.class));
    }
}