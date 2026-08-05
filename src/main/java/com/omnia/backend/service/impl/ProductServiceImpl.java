package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.ProductRequest;
import com.omnia.backend.dto.response.ProductResponse;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.mapper.ProductMapper;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.security.service.OrganizationAccessService;
import com.omnia.backend.service.interfaces.ProductService;
import com.omnia.backend.specification.ProductSpecification;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ProductServiceImpl
        implements ProductService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final List<String>
            ALLOWED_SORT_FIELDS =
            List.of(
                    "id",
                    "name",
                    "brand",
                    "price",
                    "discountPrice",
                    "stock",
                    "status",
                    "createdAt",
                    "updatedAt"
            );

    private final ProductRepository productRepository;

    private final CategoryRepository categoryRepository;

    private final OrganizationRepository
            organizationRepository;

    private final ProductMapper productMapper;

    private final OrganizationAccessService accessService;

    private final CurrentUserService currentUserService;

    public ProductServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            OrganizationRepository organizationRepository,
            ProductMapper productMapper,
            OrganizationAccessService accessService,
            CurrentUserService currentUserService
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.organizationRepository =
                organizationRepository;
        this.productMapper = productMapper;
        this.accessService = accessService;
        this.currentUserService = currentUserService;
    }

    @Override
    @Transactional
    public ProductResponse createProduct(
            ProductRequest request
    ) {
        Category category =
                findCategory(request.getCategoryId());

        User currentUser =
                currentUserService
                        .findCurrentUser()
                        .orElse(null);

        Organization organization =
                findRequestedOrganization(
                        request.getOrganizationId()
                );

        if (organization == null) {
            /*
             * Compatibility for old products while the
             * Android application is being migrated.
             */
            currentUserService.requirePlatformAdmin();
        } else if (!isPlatformAdministrator(
                currentUser
        )) {
            accessService.requireCanCreateProduct(
                    organization.getId(),
                    category.getId()
            );
        }

        Product product =
                Product.builder()
                        .name(request.getName())
                        .description(
                                request.getDescription()
                        )
                        .brand(request.getBrand())
                        .price(request.getPrice())
                        .discountPrice(
                                request.getDiscountPrice()
                        )
                        .stock(request.getStock())
                        .category(category)
                        .organization(organization)
                        .createdBy(currentUser)
                        .status(ProductStatus.ACTIVE)
                        .build();

        Product savedProduct =
                productRepository.save(product);

        return productMapper.toResponse(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {

        return productMapper.toResponse(
                findProduct(id)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> getAllProducts(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String keyword,
            Long categoryId,
            String brand,
            ProductStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        validatePagination(page, size);
        validatePriceRange(minPrice, maxPrice);

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                resolveSortDirection(sortDir),
                                resolveSortField(sortBy)
                        )
                );

        Page<ProductResponse> productPage =
                productRepository
                        .findAll(
                                ProductSpecification
                                        .filterProducts(
                                                keyword,
                                                categoryId,
                                                brand,
                                                status,
                                                minPrice,
                                                maxPrice
                                        ),
                                pageable
                        )
                        .map(productMapper::toResponse);

        return PagedResponse.from(productPage);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(
            Long id,
            ProductRequest request
    ) {
        Product product = findProduct(id);

        Category targetCategory =
                findCategory(request.getCategoryId());

        Organization currentOrganization =
                product.getOrganization();

        Organization targetOrganization =
                request.getOrganizationId() == null
                        ? currentOrganization
                        : findRequestedOrganization(
                        request.getOrganizationId()
                );

        User currentUser =
                currentUserService
                        .findCurrentUser()
                        .orElse(null);

        boolean platformAdministrator =
                isPlatformAdministrator(currentUser);

        if (!platformAdministrator) {
            requireOrganizationOwnedProduct(
                    currentOrganization
            );

            if (!sameOrganization(
                    currentOrganization,
                    targetOrganization
            )) {
                throw new AccessDeniedException(
                        "Organization administrators cannot "
                                + "transfer products to another "
                                + "organization"
                );
            }

            accessService.requireCanUpdateProduct(
                    currentOrganization.getId(),
                    product.getCategory().getId()
            );

            if (!Objects.equals(
                    product.getCategory().getId(),
                    targetCategory.getId()
            )) {
                accessService.requireCanCreateProduct(
                        currentOrganization.getId(),
                        targetCategory.getId()
                );
            }
        } else if (targetOrganization != null
                && !targetOrganization.isActive()) {
            throw new IllegalArgumentException(
                    "Organization is not active"
            );
        }

        product.setName(request.getName());
        product.setDescription(
                request.getDescription()
        );
        product.setBrand(request.getBrand());
        product.setPrice(request.getPrice());
        product.setDiscountPrice(
                request.getDiscountPrice()
        );
        product.setStock(request.getStock());
        product.setCategory(targetCategory);
        product.setOrganization(targetOrganization);

        Product updatedProduct =
                productRepository.save(product);

        return productMapper.toResponse(
                updatedProduct
        );
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {

        Product product = findProduct(id);

        User currentUser =
                currentUserService
                        .findCurrentUser()
                        .orElse(null);

        if (!isPlatformAdministrator(currentUser)) {
            Organization organization =
                    product.getOrganization();

            requireOrganizationOwnedProduct(
                    organization
            );

            accessService.requireCanDeleteProduct(
                    organization.getId(),
                    product.getCategory().getId()
            );
        }

        product.setStatus(ProductStatus.INACTIVE);

        productRepository.save(product);
    }

    private Product findProduct(Long productId) {

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException(
                    "Product id must be positive"
            );
        }

        return productRepository
                .findById(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found"
                        )
                );
    }

    private Category findCategory(Long categoryId) {

        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException(
                    "Category id must be positive"
            );
        }

        return categoryRepository
                .findById(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Category not found"
                        )
                );
    }

    private Organization findRequestedOrganization(
            Long organizationId
    ) {
        if (organizationId == null) {
            return null;
        }

        if (organizationId <= 0) {
            throw new IllegalArgumentException(
                    "Organization id must be positive"
            );
        }

        Organization organization =
                organizationRepository
                        .findById(organizationId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Organization not found"
                                )
                        );

        if (!organization.isActive()) {
            throw new IllegalArgumentException(
                    "Organization is not active"
            );
        }

        return organization;
    }

    private boolean isPlatformAdministrator(
            User currentUser
    ) {
        return currentUserService
                .hasCurrentPlatformAdminAuthority()
                || currentUserService
                .hasPlatformAdminAccess(currentUser);
    }

    private void requireOrganizationOwnedProduct(
            Organization organization
    ) {
        if (organization == null) {
            /*
             * Legacy products without an organization can
             * only be managed by the platform administrator.
             * This method throws automatically when the
             * current user is not a platform administrator.
             */
            currentUserService.requirePlatformAdmin();
        }
    }

    private boolean sameOrganization(
            Organization first,
            Organization second
    ) {
        return first != null
                && second != null
                && Objects.equals(
                first.getId(),
                second.getId()
        );
    }

    private void validatePagination(
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number must not be negative"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and "
                            + MAX_PAGE_SIZE
            );
        }
    }

    private void validatePriceRange(
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        if (minPrice != null
                && minPrice.compareTo(
                BigDecimal.ZERO
        ) < 0) {
            throw new IllegalArgumentException(
                    "Minimum price must not be negative"
            );
        }

        if (maxPrice != null
                && maxPrice.compareTo(
                BigDecimal.ZERO
        ) < 0) {
            throw new IllegalArgumentException(
                    "Maximum price must not be negative"
            );
        }

        if (minPrice != null
                && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException(
                    "Minimum price must not be greater "
                            + "than maximum price"
            );
        }
    }

    private String resolveSortField(
            String sortBy
    ) {
        if (sortBy == null || sortBy.isBlank()) {
            throw new IllegalArgumentException(
                    "Sort field must not be blank"
            );
        }

        String requestedField = sortBy.trim();

        return ALLOWED_SORT_FIELDS
                .stream()
                .filter(field ->
                        field.equalsIgnoreCase(
                                requestedField
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unsupported sort field. Allowed values: "
                                        + String.join(
                                        ", ",
                                        ALLOWED_SORT_FIELDS
                                )
                        )
                );
    }

    private Sort.Direction resolveSortDirection(
            String sortDir
    ) {
        if (sortDir == null || sortDir.isBlank()) {
            throw new IllegalArgumentException(
                    "Sort direction must not be blank"
            );
        }

        return switch (
                sortDir.trim()
                        .toLowerCase(Locale.ROOT)
                ) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new IllegalArgumentException(
                    "Sort direction must be either asc or desc"
            );
        };
    }
}