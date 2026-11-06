package com.omnia.backend.controller;

import com.omnia.backend.entity.*;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListingPromotionOfferControllerTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final OrganizationAccessService access = mock(OrganizationAccessService.class);
    private final ListingPromotionOfferController controller = new ListingPromotionOfferController(products, access);

    private Product listing() {
        var product = mock(Product.class);
        var org = mock(Organization.class);
        var category = mock(Category.class);
        when(products.findById(10L)).thenReturn(Optional.of(product));
        when(product.getOrganization()).thenReturn(org);
        when(product.getCategory()).thenReturn(category);
        when(product.getStatus()).thenReturn(ProductStatus.ACTIVE);
        when(org.getId()).thenReturn(20L);
        when(org.isActive()).thenReturn(true);
        when(category.getId()).thenReturn(30L);
        return product;
    }

    @Test void authorizedSellerGetsFixedOffer() {
        listing();
        assertEquals(new ListingPromotionOfferController.Offer(10L, new BigDecimal("5.00"), "EUR", 7),
                controller.offer(10L));
        verify(access).requireCanUpdateProduct(20L, 30L);
    }

    @Test void deniedSellerCannotReadOffer() {
        listing();
        doThrow(new AccessDeniedException("Denied")).when(access).requireCanUpdateProduct(20L, 30L);
        assertThrows(AccessDeniedException.class, () -> controller.offer(10L));
    }

    @Test void inactiveListingCannotBePromoted() {
        var product = listing();
        when(product.getStatus()).thenReturn(ProductStatus.INACTIVE);
        assertThrows(AccessDeniedException.class, () -> controller.offer(10L));
    }
}
