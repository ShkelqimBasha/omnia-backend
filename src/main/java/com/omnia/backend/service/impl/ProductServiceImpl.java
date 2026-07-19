package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.ProductRequest;
import com.omnia.backend.dto.response.ProductResponse;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Product;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.mapper.ProductMapper;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.service.interfaces.ProductService;
import com.omnia.backend.specification.ProductSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class ProductServiceImpl implements ProductService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final List<String> ALLOWED_SORT_FIELDS = List.of(
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
    private final ProductMapper productMapper;

    public ProductServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductMapper productMapper
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {

        Category category = categoryRepository
                .findById(request.getCategoryId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Category not found"
                        )
                );

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .brand(request.getBrand())
                .price(request.getPrice())
                .discountPrice(request.getDiscountPrice())
                .stock(request.getStock())
                .category(category)
                .status(ProductStatus.ACTIVE)
                .build();

        Product savedProduct =
                productRepository.save(product);

        return productMapper.toResponse(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {

        Product product = productRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found"
                        )
                );

        return productMapper.toResponse(product);
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

        String safeSortBy = resolveSortField(sortBy);
        Sort.Direction direction = resolveSortDirection(sortDir);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(direction, safeSortBy)
        );

        Page<ProductResponse> productPage = productRepository
                .findAll(
                        ProductSpecification.filterProducts(
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

        Product product = productRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found"
                        )
                );

        Category category = categoryRepository
                .findById(request.getCategoryId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Category not found"
                        )
                );

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setBrand(request.getBrand());
        product.setPrice(request.getPrice());
        product.setDiscountPrice(request.getDiscountPrice());
        product.setStock(request.getStock());
        product.setCategory(category);

        Product updatedProduct =
                productRepository.save(product);

        return productMapper.toResponse(updatedProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {

        Product product = productRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found"
                        )
                );

        product.setStatus(ProductStatus.INACTIVE);

        productRepository.save(product);
    }

    private void validatePagination(int page, int size) {

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
                && minPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Minimum price must not be negative"
            );
        }

        if (maxPrice != null
                && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Maximum price must not be negative"
            );
        }

        if (minPrice != null
                && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException(
                    "Minimum price must not be greater than maximum price"
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

        return ALLOWED_SORT_FIELDS.stream()
                .filter(field ->
                        field.equalsIgnoreCase(requestedField)
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

        return switch (sortDir.trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new IllegalArgumentException(
                    "Sort direction must be either asc or desc"
            );
        };
    }}