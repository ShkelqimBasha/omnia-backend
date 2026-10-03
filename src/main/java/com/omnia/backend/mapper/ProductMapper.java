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
                .imageUrl(primaryImageUrl(product))
                .organizationLogoUrl(organization == null || organization.getLogoFile() == null
                        ? null : "/api/files/" + organization.getLogoFile().getId())
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
    private String primaryImageUrl(Product product) {
        if (product.getImages() == null) return null;
        return product.getImages().stream()
                .filter(image -> image.getUploadedFile() != null || image.isLegacyUrlBacked())
                .sorted(java.util.Comparator
                        .comparing((com.omnia.backend.entity.ProductImage image) ->
                                !Boolean.TRUE.equals(image.getIsPrimary()))
                        .thenComparing(com.omnia.backend.entity.ProductImage::getId,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(image -> ProductImageMapper.toResponse(image).getImageUrl())
                .findFirst().orElse(null);
    }
}