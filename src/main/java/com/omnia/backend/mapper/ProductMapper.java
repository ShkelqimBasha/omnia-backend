package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.ProductResponse;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.User;
import com.omnia.backend.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(
            Product product
    ) {
        if (product == null) {
            throw new IllegalArgumentException(
                    "Product must not be null"
            );
        }

        Organization organization =
                product.getOrganization();

        User createdBy =
                product.getCreatedBy();

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .discountPrice(
                        product.getDiscountPrice()
                )
                .stock(product.getStock())
                .brand(product.getBrand())
                .category(
                        product.getCategory().getName()
                )
                .categoryId(
                        product.getCategory().getId()
                )
                .organizationId(
                        organization == null
                                ? null
                                : organization.getId()
                )
                .organizationName(
                        organization == null
                                ? null
                                : organization.getName()
                )
                .createdByUserId(
                        createdBy == null
                                ? null
                                : createdBy.getId()
                )
                .status(
                        product.getStatus().name()
                )
                .build();
    }
}