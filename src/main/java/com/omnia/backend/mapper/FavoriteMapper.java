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
                .discountPrice(favorite.getProduct().getDiscountPrice())
                .category(favorite.getProduct().getCategory().getName())
                .status(favorite.getProduct().getStatus().name())
                .build();
    }

    private FavoriteMapper() {
    }
}