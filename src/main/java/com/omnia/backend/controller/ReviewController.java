package com.omnia.backend.controller;

import com.omnia.backend.dto.request.ReviewRequest;
import com.omnia.backend.dto.response.ReviewResponse;
import com.omnia.backend.service.interfaces.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/{productId}")
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable Long productId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(reviewService.createReview(productId, request));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<ReviewResponse>> getProductReviews(
            @PathVariable Long productId
    ) {
        return ResponseEntity.ok(reviewService.getProductReviews(productId));
    }

    @GetMapping("/my")
    public ResponseEntity<List<ReviewResponse>> getMyReviews() {
        return ResponseEntity.ok(reviewService.getMyReviews());
    }

    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> updateReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return ResponseEntity.ok(reviewService.updateReview(reviewId, request));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long reviewId
    ) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.noContent().build();
    }
}