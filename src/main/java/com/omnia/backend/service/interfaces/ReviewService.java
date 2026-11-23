package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.ReviewRequest;
import com.omnia.backend.dto.response.ReviewResponse;

import java.util.List;

public interface ReviewService {

    /** Whether the signed-in user may review a product: only with a DELIVERED order of it, once. */
    record ReviewEligibility(boolean canReview, boolean verifiedBuyer, boolean alreadyReviewed) {
    }

    ReviewEligibility getEligibility(Long productId);


    ReviewResponse createReview(
            Long productId,
            ReviewRequest request
    );

    List<ReviewResponse> getProductReviews(
            Long productId
    );

    List<ReviewResponse> getMyReviews();

    ReviewResponse updateReview(
            Long reviewId,
            ReviewRequest request
    );

    void deleteReview(Long reviewId);

    List<ReviewResponse> getAllReviewsForAdmin();

    void deleteReviewForAdmin(Long reviewId);
    ReviewResponse setReviewVisibility(Long reviewId, boolean hidden);
    ReviewResponse replyToReview(Long reviewId, String reply);
    ReviewResponse removeReviewReply(Long reviewId);
}