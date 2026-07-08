package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.ProductImageRequest;
import com.omnia.backend.dto.response.ProductImageResponse;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductImage;
import com.omnia.backend.mapper.ProductImageMapper;
import com.omnia.backend.repository.ProductImageRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.service.interfaces.ProductImageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;

    public ProductImageServiceImpl(
            ProductRepository productRepository,
            ProductImageRepository imageRepository
    ) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
    }

    @Override
    @Transactional
    public ProductImageResponse addImage(Long productId, ProductImageRequest request) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        ProductImage image = ProductImage.builder()
                .product(product)
                .imageUrl(request.getImageUrl())
                .isPrimary(Boolean.TRUE.equals(request.getIsPrimary()))
                .build();

        ProductImage saved = imageRepository.save(image);

        return ProductImageMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageResponse> getProductImages(Long productId) {

        return imageRepository
                .findByProductIdOrderByIsPrimaryDescIdAsc(productId)
                .stream()
                .map(ProductImageMapper::toResponse)
                .toList();
    }
    @Override
    @Transactional
    public ProductImageResponse updateImage(Long imageId, ProductImageRequest request) {

        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));

        image.setImageUrl(request.getImageUrl());
        image.setIsPrimary(Boolean.TRUE.equals(request.getIsPrimary()));

        ProductImage updated = imageRepository.save(image);

        return ProductImageMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteImage(Long imageId) {

        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));

        imageRepository.delete(image);
    }
}