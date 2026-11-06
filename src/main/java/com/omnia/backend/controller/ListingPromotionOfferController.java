package com.omnia.backend.controller;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/listing-promotions")
public class ListingPromotionOfferController {
    private final ProductRepository products;
    private final OrganizationAccessService access;

    public ListingPromotionOfferController(ProductRepository products, OrganizationAccessService access) {
        this.products = products;
        this.access = access;
    }

    @GetMapping("/{productId}/offer")
    @Transactional(readOnly = true)
    public Offer offer(@PathVariable("productId") Long productId) {
        var product = products.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Listimi nuk u gjet."));
        var organization = product.getOrganization();
        if (organization == null || product.getCategory() == null) {
            throw new AccessDeniedException("Listimi nuk kualifikohet per promovim.");
        }
        access.requireCanUpdateProduct(organization.getId(), product.getCategory().getId());
        if (!organization.isActive() || product.getStatus() != ProductStatus.ACTIVE) {
            throw new AccessDeniedException("Listimi nuk kualifikohet per promovim.");
        }
        return new Offer(productId, new BigDecimal("5.00"), "EUR", 7);
    }

    public record Offer(Long productId, BigDecimal amount, String currency, int durationDays) {}
}
