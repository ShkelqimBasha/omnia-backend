package com.omnia.backend.service.interfaces;

import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.ProductRequest;
import com.omnia.backend.dto.response.ProductResponse;
import com.omnia.backend.enums.ProductStatus;

import java.math.BigDecimal;

public interface ProductService {

    ProductResponse createProduct(
            ProductRequest request
    );

    ProductResponse getProductById(
            Long id
    );

    PagedResponse<ProductResponse> getAllProducts(
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
    );

    ProductResponse updateProduct(
            Long id,
            ProductRequest request
    );

    void deleteProduct(
            Long id
    );
}