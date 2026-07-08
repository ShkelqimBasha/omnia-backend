package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.ProductImageRequest;
import com.omnia.backend.dto.response.ProductImageResponse;

import java.util.List;

public interface ProductImageService {

    ProductImageResponse addImage(Long productId, ProductImageRequest request);

    List<ProductImageResponse> getProductImages(Long productId);

    ProductImageResponse updateImage(Long imageId, ProductImageRequest request);

    void deleteImage(Long imageId);
}