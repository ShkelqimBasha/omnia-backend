package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.ProductRequest;
import com.omnia.backend.dto.response.ProductResponse;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Product;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.mapper.ProductMapper;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.omnia.backend.common.response.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductServiceImpl productService;

    private Category category;
    private Product product;
    private ProductRequest request;
    private ProductResponse response;

    @BeforeEach
    void setUp() {

        category = Category.builder()
                .id(1L)
                .name("Electronics")
                .build();

        request = ProductRequest.builder()
                .name("Samsung Galaxy S24")
                .description("256GB Black")
                .brand("Samsung")
                .price(new BigDecimal("1200.00"))
                .discountPrice(new BigDecimal("1099.00"))
                .stock(15)
                .categoryId(1L)
                .build();

        product = Product.builder()
                .id(1L)
                .name(request.getName())
                .description(request.getDescription())
                .brand(request.getBrand())
                .price(request.getPrice())
                .discountPrice(request.getDiscountPrice())
                .stock(request.getStock())
                .category(category)
                .status(ProductStatus.ACTIVE)
                .build();

        response = ProductResponse.builder()
                .id(1L)
                .name(product.getName())
                .description(product.getDescription())
                .brand(product.getBrand())
                .price(product.getPrice())
                .discountPrice(product.getDiscountPrice())
                .stock(product.getStock())
                .status(ProductStatus.ACTIVE.name())
                .build();
    }

    @Test
    void createProduct_shouldCreateProductSuccessfully() {

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));

        when(productRepository.save(any(Product.class)))
                .thenReturn(product);

        when(productMapper.toResponse(product))
                .thenReturn(response);

        ProductResponse result =
                productService.createProduct(request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Samsung Galaxy S24", result.getName());
        assertEquals("ACTIVE", result.getStatus());

        ArgumentCaptor<Product> productCaptor =
                ArgumentCaptor.forClass(Product.class);

        verify(productRepository).save(productCaptor.capture());

        Product savedProduct = productCaptor.getValue();

        assertEquals("Samsung Galaxy S24", savedProduct.getName());
        assertEquals("Samsung", savedProduct.getBrand());
        assertEquals(category, savedProduct.getCategory());
        assertEquals(ProductStatus.ACTIVE, savedProduct.getStatus());

        verify(categoryRepository).findById(1L);
        verify(productMapper).toResponse(product);
    }

    @Test
    void createProduct_shouldThrowWhenCategoryDoesNotExist() {

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> productService.createProduct(request)
                );

        assertEquals(
                "Category not found",
                exception.getMessage()
        );

        verify(productRepository, never())
                .save(any(Product.class));

        verifyNoInteractions(productMapper);
    }

    @Test
    void getProductById_shouldReturnProduct() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(productMapper.toResponse(product))
                .thenReturn(response);

        ProductResponse result =
                productService.getProductById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Samsung Galaxy S24", result.getName());

        verify(productRepository).findById(1L);
        verify(productMapper).toResponse(product);
    }

    @Test
    void getProductById_shouldThrowWhenProductDoesNotExist() {

        when(productRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> productService.getProductById(99L)
                );

        assertEquals(
                "Product not found",
                exception.getMessage()
        );

        verifyNoInteractions(productMapper);
    }

    @Test
    void getAllProducts_shouldReturnPagedProducts() {

        Page<Product> productPage =
                new PageImpl<>(List.of(product));

        when(productRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(productPage);

        when(productMapper.toResponse(product))
                .thenReturn(response);

        PagedResponse<ProductResponse> result =
                productService.getAllProducts(
                        0,
                        10,
                        "price",
                        "asc",
                        null,
                        null,
                        null,
                        ProductStatus.ACTIVE,
                        null,
                        null
                );

        assertNotNull(result);
        assertEquals(1, result.totalElements());
        assertEquals(
                "Samsung Galaxy S24",
                result.content().getFirst().getName()
        );

        verify(productRepository).findAll(
                any(Specification.class),
                any(Pageable.class)
        );
    }

    @Test
    void getAllProducts_shouldRejectNegativePage() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> productService.getAllProducts(
                                -1,
                                10,
                                "id",
                                "asc",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        )
                );

        assertEquals(
                "Page number must not be negative",
                exception.getMessage()
        );

        verifyNoInteractions(productRepository);
    }

    @Test
    void getAllProducts_shouldRejectInvalidPageSize() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> productService.getAllProducts(
                                0,
                                101,
                                "id",
                                "asc",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        )
                );

        assertEquals(
                "Page size must be between 1 and 100",
                exception.getMessage()
        );

        verifyNoInteractions(productRepository);
    }

    @Test
    void getAllProducts_shouldRejectInvalidPriceRange() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> productService.getAllProducts(
                                0,
                                10,
                                "id",
                                "asc",
                                null,
                                null,
                                null,
                                null,
                                new BigDecimal("1500"),
                                new BigDecimal("500")
                        )
                );

        assertEquals(
                "Minimum price must not be greater than maximum price",
                exception.getMessage()
        );

        verifyNoInteractions(productRepository);
    }

    @Test
    void updateProduct_shouldUpdateProductSuccessfully() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(category));

        when(productRepository.save(product))
                .thenReturn(product);

        when(productMapper.toResponse(product))
                .thenReturn(response);

        ProductResponse result =
                productService.updateProduct(1L, request);

        assertNotNull(result);
        assertEquals("Samsung Galaxy S24", product.getName());
        assertEquals("Samsung", product.getBrand());
        assertEquals(category, product.getCategory());

        verify(productRepository).save(product);
        verify(productMapper).toResponse(product);
    }

    @Test
    void updateProduct_shouldThrowWhenProductDoesNotExist() {

        when(productRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> productService.updateProduct(99L, request)
                );

        assertEquals(
                "Product not found",
                exception.getMessage()
        );

        verify(categoryRepository, never())
                .findById(any());

        verify(productRepository, never())
                .save(any(Product.class));
    }

    @Test
    void deleteProduct_shouldSetStatusInactive() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        productService.deleteProduct(1L);

        assertEquals(
                ProductStatus.INACTIVE,
                product.getStatus()
        );

        verify(productRepository).save(product);
    }

    @Test
    void deleteProduct_shouldThrowWhenProductDoesNotExist() {

        when(productRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> productService.deleteProduct(99L)
                );

        assertEquals(
                "Product not found",
                exception.getMessage()
        );

        verify(productRepository, never())
                .save(any(Product.class));
    }
}