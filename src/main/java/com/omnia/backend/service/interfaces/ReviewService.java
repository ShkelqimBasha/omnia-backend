package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.ReviewRequest;
import com.omnia.backend.dto.response.ReviewResponse;

import java.util.List;

public interface ReviewService {

    ReviewResponse createReview(Long productId, ReviewRequest request);

    List<ReviewResponse> getProductReviews(Long productId);

    List<ReviewResponse> getMyReviews();

    ReviewResponse updateReview(Long reviewId, ReviewRequest request);

    void deleteReview(Long reviewId);
}