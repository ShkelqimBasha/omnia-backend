package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.FavoriteResponse;
import com.omnia.backend.entity.Favorite;

public class FavoriteMapper {

    public static FavoriteResponse toResponse(Favorite favorite) {

        return FavoriteResponse.builder()
                .id(favorite.getId())
                .productId(favorite.getProduct().getId())
                .productName(favorite.getProduct().getName())
                .brand(favorite.getProduct().getBrand())
                .price(favorite.getProduct().getPrice())
                .discountPrice(com.omnia.backend.catalog.Pricing.validDiscount(
                        favorite.getProduct().getPrice(), favorite.getProduct().getDiscountPrice()))
                .category(favorite.getProduct().getCategory().getName())
                .status(favorite.getProduct().getStatus().name())
                .imageUrl(ProductMapper.primaryImageUrl(favorite.getProduct()))
                .build();
    }

    private FavoriteMapper() {
    }
}