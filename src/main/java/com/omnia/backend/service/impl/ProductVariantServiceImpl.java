package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.ProductVariantRequest;
import com.omnia.backend.dto.response.ProductVariantResponse;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductVariant;
import com.omnia.backend.entity.User;
import com.omnia.backend.mapper.ProductVariantMapper;
import com.omnia.backend.catalog.ListingRules;
import com.omnia.backend.catalog.ProductSizes;
import com.omnia.backend.catalog.SizeStock;
import com.omnia.backend.repository.CartItemRepository;
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
    private final CartItemRepository cartItemRepository;

    public ProductVariantServiceImpl(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            OrganizationAccessService accessService,
            CurrentUserService currentUserService,
            CartItemRepository cartItemRepository
    ) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.accessService = accessService;
        this.currentUserService = currentUserService;
        this.cartItemRepository = cartItemRepository;
    }

    @Override
    @Transactional
    public ProductVariantResponse addVariant(Long productId, ProductVariantRequest request) {

        // Locked like checkout: size stock and the product's sum change together.
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        requireCanManageProduct(product);

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .build();

        applyRequest(product, variant, request);

        ProductVariant saved = variantRepository.save(variant);
        product.getVariants().add(saved);
        SizeStock.syncProductStock(product);

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
        Product product = lockedProductOf(variant);

        requireCanManageProduct(product);

        applyRequest(product, variant, request);

        ProductVariant updated = variantRepository.save(variant);
        SizeStock.syncProductStock(product);

        return ProductVariantMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteVariant(Long variantId) {

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        Product product = lockedProductOf(variant);

        requireCanManageProduct(product);

        // A size chosen in an open cart stays, sold out, so that cart line keeps working.
        if (cartItemRepository.existsByVariantId(variant.getId())) {
            variant.setStock(0);
            variantRepository.save(variant);
        } else {
            product.getVariants().remove(variant);
            variantRepository.delete(variant);
        }
        SizeStock.syncProductStock(product);
    }

    private Product lockedProductOf(ProductVariant variant) {
        Long productId = variant.getProduct().getId();
        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    /*
     * Variants are sizes (see ProductSizes): name "SIZE", a value allowed for
     * the product's category (clothing or shoes), one row per value, no
     * per-size price yet.
     */
    private void applyRequest(
            Product product,
            ProductVariant variant,
            ProductVariantRequest request
    ) {
        if (!ProductSizes.VARIANT_NAME.equalsIgnoreCase(request.getVariantName().trim())) {
            throw new IllegalArgumentException("Only SIZE variants are supported");
        }
        String value = ProductSizes.normalizeValue(
                ListingRules.kindOf(product), request.getVariantValue());
        boolean duplicate = product.getVariants().stream()
                .anyMatch(other -> other != variant
                        && !java.util.Objects.equals(other.getId(), variant.getId())
                        && ProductSizes.isSize(other)
                        && value.equals(other.getVariantValue()));
        if (duplicate) {
            throw new IllegalArgumentException("Masa ekziston tashmë: " + ProductSizes.label(value));
        }
        if (request.getPriceAdjustment() != null && request.getPriceAdjustment().signum() != 0) {
            throw new IllegalArgumentException("Sizes cannot change the price");
        }
        variant.setVariantName(ProductSizes.VARIANT_NAME);
        variant.setVariantValue(value);
        variant.setPriceAdjustment(BigDecimal.ZERO);
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
