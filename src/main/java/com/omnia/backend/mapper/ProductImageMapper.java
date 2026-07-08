package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.ProductImageResponse;
import com.omnia.backend.entity.ProductImage;

public class ProductImageMapper {

    public static ProductImageResponse toResponse(ProductImage image) {

        return ProductImageResponse.builder()
                .id(image.getId())
                .productId(image.getProduct().getId())
                .imageUrl(image.getImageUrl())
                .isPrimary(image.getIsPrimary())
                .createdAt(image.getCreatedAt())
                .build();
    }

    private ProductImageMapper() {
    }
}