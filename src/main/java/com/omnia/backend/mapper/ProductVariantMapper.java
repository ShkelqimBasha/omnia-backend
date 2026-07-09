package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.ProductVariantResponse;
import com.omnia.backend.entity.ProductVariant;

public class ProductVariantMapper {

    public static ProductVariantResponse toResponse(ProductVariant variant) {

        return ProductVariantResponse.builder()
                .id(variant.getId())
                .productId(variant.getProduct().getId())
                .variantName(variant.getVariantName())
                .variantValue(variant.getVariantValue())
                .priceAdjustment(variant.getPriceAdjustment())
                .stock(variant.getStock())
                .build();
    }

    private ProductVariantMapper() {
    }
}