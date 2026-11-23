package com.omnia.backend.service.impl;

import com.omnia.backend.dto.request.ProductRequest;
import com.omnia.backend.dto.request.ProductRequest.SizeRequest;
import com.omnia.backend.dto.response.ProductResponse;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductVariant;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.mapper.ProductMapper;
import com.omnia.backend.repository.CartItemRepository;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.ProductVariantRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Product create / update with sizes (ProductRequest.sizes) and the product payload. */
@ExtendWith(MockitoExtension.class)
class ProductSizesServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationAccessService accessService;
    @Mock private CurrentUserService currentUserService;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private CartItemRepository cartItemRepository;

    private ProductServiceImpl service;
    private Category clothing;
    private Category shoes;
    private Category general;
    private final AtomicLong ids = new AtomicLong(500);

    @BeforeEach
    void setUp() {
        service = new ProductServiceImpl(productRepository, categoryRepository, organizationRepository,
                new ProductMapper(), accessService, currentUserService);
        service.setSizeWriter(new ProductSizeWriter(variantRepository, cartItemRepository));
        lenient().when(currentUserService.hasCurrentPlatformAdminAuthority()).thenReturn(true);

        clothing = Category.builder().id(1L).name("Veshje").kind(CategoryKind.CLOTHING).build();
        shoes = Category.builder().id(2L).name("Atlete & Këpucë").kind(CategoryKind.SHOES).build();
        general = Category.builder().id(3L).name("Elektronike").kind(CategoryKind.GENERAL).build();
        for (Category c : List.of(clothing, shoes, general)) {
            lenient().when(categoryRepository.findById(c.getId())).thenReturn(Optional.of(c));
        }
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(10L);
            return p;
        });
        lenient().when(variantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> {
            ProductVariant v = invocation.getArgument(0);
            if (v.getId() == null) v.setId(ids.incrementAndGet());
            return v;
        });
    }

    private static ProductRequest request(long categoryId, Integer stock, List<SizeRequest> sizes) {
        return ProductRequest.builder().name("Bluzë").price(new BigDecimal("20.00"))
                .categoryId(categoryId).stock(stock).sizes(sizes).build();
    }

    private static SizeRequest size(String value, int stock) {
        return SizeRequest.builder().value(value).stock(stock).build();
    }

    private Product existing(Category category, String... valuesAndStock) {
        Product p = Product.builder().id(10L).name("Bluzë").price(new BigDecimal("20.00"))
                .category(category).status(ProductStatus.ACTIVE).stock(0).build();
        long id = 100;
        int total = 0;
        for (int i = 0; i < valuesAndStock.length; i += 2) {
            int stock = Integer.parseInt(valuesAndStock[i + 1]);
            p.getVariants().add(ProductVariant.builder().id(id++).product(p).variantName("SIZE")
                    .variantValue(valuesAndStock[i]).priceAdjustment(BigDecimal.ZERO).stock(stock).build());
            total += stock;
        }
        p.setStock(total);
        when(productRepository.findById(10L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void createWithSizesStoresOneRowPerSizeAndTheSum() {
        ProductResponse response = service.createProduct(request(1L, 99,
                List.of(size("l", 0), size("M", 3), size("3XL", 2))));

        assertEquals(5, response.getStock(), "stock sent by the client is ignored");
        assertTrue(response.isSizeRequired());
        assertEquals(List.of("M", "L", "XXXL"),
                response.getSizes().stream().map(ProductResponse.SizeView::getValue).toList());
        assertEquals(List.of("M", "L", "3XL"),
                response.getSizes().stream().map(ProductResponse.SizeView::getLabel).toList());
        assertEquals(0, response.getSizes().get(1).getStock());
        assertNotNull(response.getSizes().get(0).getId());
        verify(variantRepository, times(3)).save(any(ProductVariant.class));
    }

    @Test
    void createRefusesSizesOutsideClothingAndShoesAndBadValues() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createProduct(request(3L, 1, List.of(size("M", 1)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.createProduct(request(2L, null, List.of(size("M", 1)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.createProduct(request(2L, null, List.of(size("42", 1), size("42.0", 1)))));
        verify(productRepository, never()).save(any());
    }

    @Test
    void createWithoutSizesNeedsAStockAndShowsNoSizes() {
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(request(3L, null, null)));

        ProductResponse response = service.createProduct(request(1L, 7, null));
        assertEquals(7, response.getStock());
        assertFalse(response.isSizeRequired());
        assertTrue(response.getSizes().isEmpty());
        verifyNoInteractions(variantRepository);
    }

    @Test
    void updateReplacesSizesKeepingIdsOfUnchangedValues() {
        Product p = existing(clothing, "M", "3", "L", "2");
        Long mediumId = p.getVariants().get(0).getId();

        ProductResponse response = service.updateProduct(10L, request(1L, null,
                List.of(size("m", 5), size("XL", 1))));

        assertEquals(List.of("M", "XL"),
                response.getSizes().stream().map(ProductResponse.SizeView::getValue).toList());
        assertEquals(mediumId, response.getSizes().get(0).getId());
        assertEquals(6, response.getStock());
        verify(variantRepository).delete(argThat(v -> "L".equals(v.getVariantValue())));
        verify(productRepository).findByIdForUpdate(10L);
    }

    @Test
    void aRemovedSizeInAnOpenCartStaysSoldOut() {
        Product p = existing(clothing, "M", "3", "L", "2");
        Long largeId = p.getVariants().get(1).getId();
        when(cartItemRepository.existsByVariantId(largeId)).thenReturn(true);

        ProductResponse response = service.updateProduct(10L, request(1L, null, List.of(size("M", 3))));

        assertEquals(List.of("M", "L"),
                response.getSizes().stream().map(ProductResponse.SizeView::getValue).toList());
        assertEquals(0, response.getSizes().get(1).getStock());
        assertEquals(3, response.getStock());
        verify(variantRepository, never()).delete(any());
    }

    @Test
    void updateWithoutSizesFieldLeavesThemAndIgnoresTheStock() {
        existing(clothing, "M", "3");

        ProductResponse response = service.updateProduct(10L, request(1L, 50, null));

        assertEquals(3, response.getStock());
        assertEquals(1, response.getSizes().size());
        verifyNoInteractions(variantRepository);
    }

    @Test
    void emptySizesMakeTheProductUnsizedAndDropCartLinesOfThoseSizes() {
        Product p = existing(clothing, "M", "3");
        Long mediumId = p.getVariants().get(0).getId();

        assertThrows(IllegalArgumentException.class,
                () -> service.updateProduct(10L, request(1L, null, List.of())),
                "stock is required once the product has no sizes");

        p = existing(clothing, "M", "3");
        ProductResponse response = service.updateProduct(10L, request(1L, 4, new ArrayList<>()));
        assertFalse(response.isSizeRequired());
        assertEquals(4, response.getStock());
        verify(cartItemRepository).deleteByVariantId(mediumId);
        assertTrue(p.getVariants().isEmpty());
    }

    @Test
    void movingToACategoryWithoutSizesDropsThem() {
        Product p = existing(clothing, "M", "3");
        ProductResponse response = service.updateProduct(10L, request(3L, 8, null));
        assertEquals(8, response.getStock());
        assertTrue(response.getSizes().isEmpty());
        assertTrue(p.getVariants().isEmpty());
    }

    @Test
    void movingFromClothingToShoesNeedsTheNewSizes() {
        existing(clothing, "M", "3");
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(10L, request(2L, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateProduct(10L, request(2L, null, List.of(size("M", 1)))));

        ProductResponse response = service.updateProduct(10L, request(2L, null, List.of(size("41", 2))));
        assertEquals(List.of("41"), response.getSizes().stream().map(ProductResponse.SizeView::getValue).toList());
        assertEquals(2, response.getStock());
    }
}
