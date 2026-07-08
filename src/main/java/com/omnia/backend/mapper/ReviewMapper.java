package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.ReviewResponse;
import com.omnia.backend.entity.Review;

public class ReviewMapper {

    public static ReviewResponse toResponse(Review review) {

        return ReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUser().getId())
                .username(review.getUser().getUsername())
                .productId(review.getProduct().getId())
                .productName(review.getProduct().getName())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }

    private ReviewMapper() {
    }
}