package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.ReviewRequest;
import com.omnia.backend.dto.response.ReviewResponse;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.Review;
import com.omnia.backend.entity.User;
import com.omnia.backend.mapper.ReviewMapper;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.ReviewRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.interfaces.ReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ReviewServiceImpl(
            ReviewRepository reviewRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public ReviewResponse createReview(Long productId, ReviewRequest request) {

        User user = getCurrentUser();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (reviewRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new ResourceAlreadyExistsException("You have already reviewed this product");
        }

        Review review = Review.builder()
                .user(user)
                .product(product)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        Review saved = reviewRepository.save(review);

        return ReviewMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponse> getProductReviews(Long productId) {

        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found");
        }

        return reviewRepository.findByProductId(productId)
                .stream()
                .map(ReviewMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponse> getMyReviews() {

        User user = getCurrentUser();

        return reviewRepository.findByUserId(user.getId())
                .stream()
                .map(ReviewMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(Long reviewId, ReviewRequest request) {

        User user = getCurrentUser();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        if (!review.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You are not allowed to update this review");
        }

        review.setRating(request.getRating());
        review.setComment(request.getComment());

        Review updated = reviewRepository.save(review);

        return ReviewMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteReview(Long reviewId) {

        User user = getCurrentUser();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        if (!review.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You are not allowed to delete this review");
        }

        reviewRepository.delete(review);
    }

    private User getCurrentUser() {

        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        String usernameOrEmail = authentication.getName();

        return userRepository.findByEmail(usernameOrEmail)
                .or(() -> userRepository.findByUsername(usernameOrEmail))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}