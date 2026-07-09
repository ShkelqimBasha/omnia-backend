package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.ProductVariantRequest;
import com.omnia.backend.dto.response.ProductVariantResponse;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductVariant;
import com.omnia.backend.mapper.ProductVariantMapper;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.ProductVariantRepository;
import com.omnia.backend.service.interfaces.ProductVariantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    public ProductVariantServiceImpl(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository
    ) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    @Override
    @Transactional
    public ProductVariantResponse addVariant(Long productId, ProductVariantRequest request) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .variantName(request.getVariantName())
                .variantValue(request.getVariantValue())
                .priceAdjustment(request.getPriceAdjustment())
                .stock(request.getStock())
                .build();

        ProductVariant saved = variantRepository.save(variant);

        return ProductVariantMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getProductVariants(Long productId) {

        return variantRepository.findByProductId(productId)
                .stream()
                .map(ProductVariantMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductVariantResponse updateVariant(Long variantId, ProductVariantRequest request) {

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

        variant.setVariantName(request.getVariantName());
        variant.setVariantValue(request.getVariantValue());
        variant.setPriceAdjustment(request.getPriceAdjustment());
        variant.setStock(request.getStock());

        ProductVariant updated = variantRepository.save(variant);

        return ProductVariantMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteVariant(Long variantId) {

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

        variantRepository.delete(variant);
    }
}