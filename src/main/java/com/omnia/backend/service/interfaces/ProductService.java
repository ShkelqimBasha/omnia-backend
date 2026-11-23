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
            Long organizationId,
            String brand,
            ProductStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice
    );

    /** Same as above, plus filters on category-specific listing fields. */
    default PagedResponse<ProductResponse> getAllProducts(
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
            java.util.List<com.omnia.backend.catalog.AttributeFilter> attributeFilters
    ) {
        if (attributeFilters != null && !attributeFilters.isEmpty()) {
            throw new UnsupportedOperationException("Attribute filters are not supported");
        }
        return getAllProducts(page, size, sortBy, sortDir, keyword, categoryId,
                organizationId, brand, status, minPrice, maxPrice);
    }

    ProductResponse updateProduct(
            Long id,
            ProductRequest request
    );

    void deleteProduct(
            Long id
    );
}