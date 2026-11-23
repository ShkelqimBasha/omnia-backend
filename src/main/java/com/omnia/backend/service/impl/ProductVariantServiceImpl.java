package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.ProductVariantRequest;
import com.omnia.backend.dto.response.ProductVariantResponse;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductVariant;
import com.omnia.backend.entity.User;
import com.omnia.backend.mapper.ProductVariantMapper;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.ProductVariantRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.security.service.OrganizationAccessService;
import com.omnia.backend.service.interfaces.ProductVariantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final OrganizationAccessService accessService;
    private final CurrentUserService currentUserService;

    public ProductVariantServiceImpl(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            OrganizationAccessService accessService,
            CurrentUserService currentUserService
    ) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.accessService = accessService;
        this.currentUserService = currentUserService;
    }

    @Override
    @Transactional
    public ProductVariantResponse addVariant(Long productId, ProductVariantRequest request) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        requireCanManageProduct(product);

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .build();

        applyRequest(variant, request);

        ProductVariant saved = variantRepository.save(variant);

        return ProductVariantMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getProductVariants(Long productId) {

        return variantRepository.findByProductId(productId)
                .stream()
                .map(ProductVariantMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductVariantResponse updateVariant(Long variantId, ProductVariantRequest request) {

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

        requireCanManageProduct(variant.getProduct());

        applyRequest(variant, request);

        ProductVariant updated = variantRepository.save(variant);

        return ProductVariantMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteVariant(Long variantId) {

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

        requireCanManageProduct(variant.getProduct());

        variantRepository.delete(variant);
    }

    private void applyRequest(
            ProductVariant variant,
            ProductVariantRequest request
    ) {
        variant.setVariantName(request.getVariantName().trim());
        variant.setVariantValue(request.getVariantValue().trim());
        variant.setPriceAdjustment(
                request.getPriceAdjustment() == null
                        ? BigDecimal.ZERO
                        : request.getPriceAdjustment()
        );
        variant.setStock(request.getStock());
    }

    /*
     * Same rule as product images: a platform administrator,
     * or an OWNER/ADMIN of the product's organization whose
     * organization may update products in the product's
     * category. Products without an organization (or without
     * a category) can only be managed by a platform admin.
     */
    private void requireCanManageProduct(Product product) {

        User currentUser = currentUserService
                .findCurrentUser()
                .orElse(null);

        if (currentUserService.hasCurrentPlatformAdminAuthority()
                || currentUserService.hasPlatformAdminAccess(currentUser)) {
            return;
        }

        Organization organization = product.getOrganization();

        if (organization == null || product.getCategory() == null) {
            currentUserService.requirePlatformAdmin();
            return;
        }

        accessService.requireCanUpdateProduct(
                organization.getId(),
                product.getCategory().getId()
        );
    }
}
