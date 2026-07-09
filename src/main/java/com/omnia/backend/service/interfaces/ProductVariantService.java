package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.ProductVariantRequest;
import com.omnia.backend.dto.response.ProductVariantResponse;

import java.util.List;

public interface ProductVariantService {

    ProductVariantResponse addVariant(Long productId, ProductVariantRequest request);

    List<ProductVariantResponse> getProductVariants(Long productId);

    ProductVariantResponse updateVariant(Long variantId, ProductVariantRequest request);

    void deleteVariant(Long variantId);
}