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
    @org.springframework.beans.factory.annotation.Autowired
    private NotificationWriter notificationWriter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.omnia.backend.catalog.CatalogSettings catalogSettings;

    private ProductSizeWriter sizeWriter;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSizeWriter(ProductSizeWriter sizeWriter) {
        this.sizeWriter = sizeWriter;
    }


    private static final int MAX_PAGE_SIZE = 100;

    /** Time zone of the dates in product_daily_stats (see ProductActivityCounter). */
    private static final java.time.ZoneId TIRANA = java.time.ZoneId.of("Europe/Tirane");

    private ProductRatings productRatings;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setProductRatings(ProductRatings productRatings) {
        this.productRatings = productRatings;
    }

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
        requireActiveCategory(category);

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
            requirePrivateSellerRoom(organization);
        }

        com.omnia.backend.enums.CategoryKind kind =
                com.omnia.backend.enums.CategoryKind.fromNullable(category.getKind());
        com.omnia.backend.catalog.ListingRules.NormalizedListing listing =
                com.omnia.backend.catalog.ListingRules.normalize(
                        kind, request.getAttributes(), request.getBrand());
        List<com.omnia.backend.catalog.ProductSizes.SizeInput> sizes =
                com.omnia.backend.catalog.ProductSizes.normalize(kind, sizeInputs(request));
        if (sizes.isEmpty()) {
            requireStock(request);
        }

        Product product =
                Product.builder()
                        .name(request.getName())
                        .description(
                                request.getDescription()
                        )
                        .brand(listing.brand())
                        .attributes(listing.attributes())
                        .saleMode(com.omnia.backend.catalog.ListingRules.resolveSaleMode(
                                kind, request.getSaleMode(), carOnline()))
                        .price(request.getPrice())
                        .discountPrice(
                                com.omnia.backend.catalog.Pricing.normalizeDiscount(
                                        request.getPrice(), request.getDiscountPrice())
                        )
                        .stock(sizes.isEmpty() ? request.getStock() : 0)
                        .category(category)
                        .organization(organization)
                        .createdBy(currentUser)
                        .status(ProductStatus.ACTIVE)
                        .build();

        Product savedProduct =
                productRepository.save(product);
        if (!sizes.isEmpty()) {
            sizeWriter().replace(savedProduct, sizes);
        }

        if (notificationWriter != null) notificationWriter.low(savedProduct, "create");
        return productMapper.toResponse(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {

        ProductResponse response = productMapper.toResponse(
                findVisibleProduct(id)
        );
        if (productRatings != null) {
            productRatings.fill(List.of(response));
        }
        return response;
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
            Long organizationId,
            String brand,
            ProductStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        return getAllProducts(page, size, sortBy, sortDir, keyword, categoryId,
                organizationId, brand, status, minPrice, maxPrice, List.of());
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
            Long organizationId,
            String brand,
            ProductStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<com.omnia.backend.catalog.AttributeFilter> attributeFilters
    ) {
        return getAllProducts(page, size, sortBy, sortDir, keyword, categoryId,
                organizationId, brand, status, minPrice, maxPrice, attributeFilters, List.of());
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
            Long organizationId,
            String brand,
            ProductStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<com.omnia.backend.catalog.AttributeFilter> attributeFilters,
            List<String> sizes
    ) {
        return getAllProducts(page, size, sortBy, sortDir, keyword, categoryId, organizationId,
                brand, status, minPrice, maxPrice, attributeFilters, sizes,
                com.omnia.backend.catalog.CatalogFilters.NONE);
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
            Long organizationId,
            String brand,
            ProductStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<com.omnia.backend.catalog.AttributeFilter> attributeFilters,
            List<String> sizes,
            com.omnia.backend.catalog.CatalogFilters catalogFilters
    ) {
        validatePagination(page, size);
        validatePriceRange(minPrice, maxPrice);

        if (organizationId != null
                && organizationId <= 0) {
            throw new IllegalArgumentException(
                    "Organization id must be positive"
            );
        }

        Sort.Direction direction = resolveSortDirection(sortDir);
        java.util.Optional<com.omnia.backend.catalog.CatalogSort> catalogSort =
                com.omnia.backend.catalog.CatalogSort.fromKey(sortBy);

        // Catalog sorts order by subqueries inside the specification; the Pageable stays unsorted.
        Pageable pageable = catalogSort.isPresent()
                ? PageRequest.of(page, size)
                : PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                direction,
                                resolveSortField(sortBy)
                        )
                );

        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        // Hidden products are listed only for their own company's members and platform admins.
        boolean seesHidden = accessService.canSeeHiddenProducts(organizationId);

        Page<ProductResponse> productPage =
                productRepository
                        .findAll(
                                ProductSpecification
                                        .filterProducts(
                                                keyword,
                                                categoryId,
                                                organizationId,
                                                brand,
                                                status,
                                                minPrice,
                                                maxPrice
                                        ).and(ProductSpecification
                                        .hasAttributes(attributeFilters))
                                        .and(ProductSpecification
                                        .hasAnySize(sizes))
                                        .and(seesHidden ? null : ProductSpecification.notHidden())
                                        .and(ProductSpecification.catalogFilters(
                                                catalogFilters,
                                                com.omnia.backend.catalog.CatalogSettings
                                                        .carOnline(catalogSettings),
                                                now))
                                        .and(ProductSpecification.sortedBy(
                                                catalogSort.orElse(null),
                                                keyword,
                                                java.time.LocalDate.now(TIRANA))),
                                pageable
                        )
                        .map(productMapper::toResponse);

        if (productRatings != null) {
            productRatings.fill(productPage.getContent());
        }
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

        if (!Objects.equals(
                product.getCategory().getId(),
                targetCategory.getId()
        )) {
            requireActiveCategory(targetCategory);
        }

        com.omnia.backend.enums.CategoryKind targetKind =
                com.omnia.backend.enums.CategoryKind.fromNullable(targetCategory.getKind());
        boolean sameCategory = Objects.equals(
                product.getCategory().getId(), targetCategory.getId());
        /*
         * Older clients do not send attributes or a sale mode: keep the
         * stored ones instead of wiping them.
         */
        java.util.Map<String, Object> rawAttributes =
                request.getAttributes() == null && sameCategory
                        ? product.getAttributes()
                        : request.getAttributes();
        String requestedSaleMode =
                request.getSaleMode() == null && sameCategory && product.getSaleMode() != null
                        ? product.getSaleMode().name()
                        : request.getSaleMode();
        com.omnia.backend.catalog.ListingRules.NormalizedListing listing =
                com.omnia.backend.catalog.ListingRules.normalize(
                        targetKind, rawAttributes, request.getBrand());

        product.setName(request.getName());
        product.setDescription(
                request.getDescription()
        );
        product.setBrand(listing.brand());
        product.setAttributes(listing.attributes());
        product.setSaleMode(com.omnia.backend.catalog.ListingRules.resolveSaleMode(
                targetKind, requestedSaleMode, carOnline()));
        BigDecimal previousBuyerPrice = buyerPrice(product);
        product.setPrice(request.getPrice());
        product.setDiscountPrice(
                com.omnia.backend.catalog.Pricing.normalizeDiscount(
                        request.getPrice(), request.getDiscountPrice())
        );
        Integer notificationPreviousStock = product.getStock();
        com.omnia.backend.enums.CategoryKind previousKind =
                com.omnia.backend.catalog.ListingRules.kindOf(product);
        List<com.omnia.backend.catalog.ProductSizes.SizeInput> sizes =
                sizesForUpdate(product, previousKind, targetKind, request);
        if (sizes != null && sizes.isEmpty()) {
            requireStock(request);
        }
        product.setCategory(targetCategory);
        product.setOrganization(targetOrganization);
        if (sizes != null) {
            // Sizes change: hold the product row like checkout does while stock moves.
            productRepository.findByIdForUpdate(product.getId());
            sizeWriter().replace(product, sizes);
        }
        if (com.omnia.backend.catalog.ProductSizes.sizesOf(product).isEmpty()) {
            requireStock(request);
            product.setStock(request.getStock());
        } else {
            // Product stock is the sum of its sizes; a stock sent by the client is ignored.
            com.omnia.backend.catalog.SizeStock.syncProductStock(product);
        }

        Product updatedProduct =
                productRepository.save(product);

        if (notificationWriter != null && !Objects.equals(notificationPreviousStock, updatedProduct.getStock()))
            notificationWriter.low(updatedProduct, java.util.UUID.randomUUID().toString());
        BigDecimal newBuyerPrice = buyerPrice(updatedProduct);
        if (notificationWriter != null && updatedProduct.getStatus() == ProductStatus.ACTIVE
                && previousBuyerPrice != null && newBuyerPrice != null
                && newBuyerPrice.compareTo(previousBuyerPrice) < 0) {
            notificationWriter.priceDrop(updatedProduct, previousBuyerPrice, newBuyerPrice);
        }
        return productMapper.toResponse(
                updatedProduct
        );
    }

    /** The price a buyer pays: the discount price when it is set and lower than the price. */
    static BigDecimal buyerPrice(Product product) {
        return com.omnia.backend.catalog.Pricing.buyerPrice(product);
    }

    private static List<com.omnia.backend.catalog.ProductSizes.SizeInput> sizeInputs(
            ProductRequest request) {
        if (request.getSizes() == null) return null;
        return request.getSizes().stream()
                .map(size -> size == null ? null
                        : new com.omnia.backend.catalog.ProductSizes.SizeInput(
                                size.getValue(), size.getStock()))
                .toList();
    }

    private static void requireStock(ProductRequest request) {
        if (request.getStock() == null) {
            throw new IllegalArgumentException("Stock is required for a product without sizes");
        }
    }

    /**
     * The sizes an update writes, or null to leave them as they are.
     * Sent sizes replace the current ones. Moving to a category without sizes
     * drops them. Moving between clothing and shoes needs the new sizes, since
     * "M" is not a shoe number.
     */
    private List<com.omnia.backend.catalog.ProductSizes.SizeInput> sizesForUpdate(
            Product product,
            com.omnia.backend.enums.CategoryKind previousKind,
            com.omnia.backend.enums.CategoryKind targetKind,
            ProductRequest request) {
        boolean hasSizes = product.getVariants().stream()
                .anyMatch(com.omnia.backend.catalog.ProductSizes::isSize);
        if (request.getSizes() != null) {
            List<com.omnia.backend.catalog.ProductSizes.SizeInput> sizes =
                    com.omnia.backend.catalog.ProductSizes.normalize(targetKind, sizeInputs(request));
            return sizes.isEmpty() && !hasSizes ? null : sizes;
        }
        if (!hasSizes) return null;
        if (!com.omnia.backend.catalog.ProductSizes.supportsSizes(targetKind)) return List.of();
        if (previousKind != targetKind) {
            throw new IllegalArgumentException("Dërgo masat për kategorinë e re");
        }
        return null;
    }

    private ProductSizeWriter sizeWriter() {
        if (sizeWriter == null) throw new IllegalStateException("Product sizes are not available");
        return sizeWriter;
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

    /** A product the caller may see: hidden ones only for their company and platform admins. */
    private Product findVisibleProduct(Long productId) {
        Product product = findProduct(productId);
        if (product.getStatus() == ProductStatus.INACTIVE) {
            Long organizationId = product.getOrganization() == null
                    ? null : product.getOrganization().getId();
            if (!accessService.canSeeHiddenProducts(organizationId)) {
                throw new ResourceNotFoundException("Product not found");
            }
        }
        return product;
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

    private void requireActiveCategory(Category category) {
        if (category.getStatus() == com.omnia.backend.enums.CategoryStatus.INACTIVE) {
            throw new IllegalArgumentException(
                    "New listings are not accepted in this category"
            );
        }
    }

    private boolean carOnline() {
        return com.omnia.backend.catalog.CatalogSettings.carOnline(catalogSettings);
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

    /**
     * A PRIVATE seller ("Shitës privat") may have at most 30 ACTIVE products. Platform admins
     * bypass it (callers only invoke this for non-admins). The company row is locked so two
     * concurrent creates cannot both take the last place.
     */
    private void requirePrivateSellerRoom(Organization organization) {
        if (organization.getSellerType() != com.omnia.backend.enums.SellerType.PRIVATE) {
            return;
        }
        organizationRepository.findForEditor(organization.getId());
        long active = productRepository.countByOrganizationIdAndStatus(
                organization.getId(), ProductStatus.ACTIVE);
        if (active >= SellerApplicationRules.PRIVATE_ACTIVE_PRODUCT_LIMIT) {
            throw new IllegalArgumentException(SellerApplicationRules.PRIVATE_LIMIT_MESSAGE);
        }
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
                                        + ", "
                                        + java.util.Arrays.stream(
                                                com.omnia.backend.catalog.CatalogSort.values())
                                        .map(com.omnia.backend.catalog.CatalogSort::key)
                                        .collect(java.util.stream.Collectors.joining(", "))
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
