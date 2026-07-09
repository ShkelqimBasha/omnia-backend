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
import com.omnia.backend.service.interfaces.ProductService;
import com.omnia.backend.specification.ProductSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ProductServiceImpl implements ProductService {

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

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

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

        Product savedProduct = productRepository.save(product);

        return productMapper.toResponse(savedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(
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

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return productRepository
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
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setBrand(request.getBrand());
        product.setPrice(request.getPrice());
        product.setDiscountPrice(request.getDiscountPrice());
        product.setStock(request.getStock());
        product.setCategory(category);

        Product updatedProduct = productRepository.save(product);

        return productMapper.toResponse(updatedProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        product.setStatus(ProductStatus.INACTIVE);

        productRepository.save(product);
    }
}