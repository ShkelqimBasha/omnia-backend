package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.ProductResponse;
import com.omnia.backend.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .discountPrice(product.getDiscountPrice())
                .stock(product.getStock())
                .brand(product.getBrand())
                .category(product.getCategory().getName())
                .status(product.getStatus().name())
                .build();
    }
}