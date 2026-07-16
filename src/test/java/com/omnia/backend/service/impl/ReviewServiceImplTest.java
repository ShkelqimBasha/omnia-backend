package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.ReviewRequest;
import com.omnia.backend.dto.response.ReviewResponse;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.Review;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.ReviewRepository;
import com.omnia.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private User currentUser;
    private User anotherUser;
    private Product product;
    private Review review;
    private ReviewRequest request;

    @BeforeEach
    void setUp() {

        currentUser = User.builder()
                .id(1L)
                .username("shkelqim")
                .email("shkelqim@example.com")
                .build();

        anotherUser = User.builder()
                .id(2L)
                .username("anotherUser")
                .email("another@example.com")
                .build();

        product = Product.builder()
                .id(10L)
                .name("Samsung Galaxy S24")
                .build();

        review = Review.builder()
                .id(100L)
                .user(currentUser)
                .product(product)
                .rating(5)
                .comment("Excellent product")
                .createdAt(
                        LocalDateTime.of(
                                2026,
                                7,
                                16,
                                19,
                                30
                        )
                )
                .build();

        request = ReviewRequest.builder()
                .rating(5)
                .comment("Excellent product")
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "shkelqim@example.com",
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReview_shouldCreateReviewSuccessfully() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(reviewRepository.existsByUserIdAndProductId(
                1L,
                10L
        )).thenReturn(false);

        when(reviewRepository.save(any(Review.class)))
                .thenReturn(review);

        ReviewResponse result =
                reviewService.createReview(10L, request);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(1L, result.getUserId());
        assertEquals("shkelqim", result.getUsername());
        assertEquals(10L, result.getProductId());
        assertEquals(
                "Samsung Galaxy S24",
                result.getProductName()
        );
        assertEquals(5, result.getRating());
        assertEquals(
                "Excellent product",
                result.getComment()
        );

        ArgumentCaptor<Review> reviewCaptor =
                ArgumentCaptor.forClass(Review.class);

        verify(reviewRepository)
                .save(reviewCaptor.capture());

        Review savedReview = reviewCaptor.getValue();

        assertEquals(currentUser, savedReview.getUser());
        assertEquals(product, savedReview.getProduct());
        assertEquals(5, savedReview.getRating());
        assertEquals(
                "Excellent product",
                savedReview.getComment()
        );
    }

    @Test
    void createReview_shouldThrowWhenProductDoesNotExist() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(productRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> reviewService.createReview(
                                99L,
                                request
                        )
                );

        assertEquals(
                "Product not found",
                exception.getMessage()
        );

        verify(reviewRepository, never())
                .save(any(Review.class));
    }

    @Test
    void createReview_shouldThrowWhenUserAlreadyReviewedProduct() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(reviewRepository.existsByUserIdAndProductId(
                1L,
                10L
        )).thenReturn(true);

        ResourceAlreadyExistsException exception =
                assertThrows(
                        ResourceAlreadyExistsException.class,
                        () -> reviewService.createReview(
                                10L,
                                request
                        )
                );

        assertEquals(
                "You have already reviewed this product",
                exception.getMessage()
        );

        verify(reviewRepository, never())
                .save(any(Review.class));
    }

    @Test
    void getProductReviews_shouldReturnReviews() {

        when(productRepository.existsById(10L))
                .thenReturn(true);

        when(reviewRepository.findByProductId(10L))
                .thenReturn(List.of(review));

        List<ReviewResponse> result =
                reviewService.getProductReviews(10L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(100L, result.getFirst().getId());
        assertEquals(5, result.getFirst().getRating());
        assertEquals(
                "Samsung Galaxy S24",
                result.getFirst().getProductName()
        );

        verify(reviewRepository)
                .findByProductId(10L);
    }

    @Test
    void getProductReviews_shouldReturnEmptyList() {

        when(productRepository.existsById(10L))
                .thenReturn(true);

        when(reviewRepository.findByProductId(10L))
                .thenReturn(List.of());

        List<ReviewResponse> result =
                reviewService.getProductReviews(10L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getProductReviews_shouldThrowWhenProductDoesNotExist() {

        when(productRepository.existsById(99L))
                .thenReturn(false);

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> reviewService.getProductReviews(99L)
                );

        assertEquals(
                "Product not found",
                exception.getMessage()
        );

        verify(reviewRepository, never())
                .findByProductId(anyLong());
    }

    @Test
    void getMyReviews_shouldReturnCurrentUsersReviews() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(reviewRepository.findByUserId(1L))
                .thenReturn(List.of(review));

        List<ReviewResponse> result =
                reviewService.getMyReviews();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("shkelqim", result.getFirst().getUsername());

        verify(reviewRepository)
                .findByUserId(1L);
    }

    @Test
    void updateReview_shouldUpdateReviewSuccessfully() {

        ReviewRequest updateRequest =
                ReviewRequest.builder()
                        .rating(4)
                        .comment("Very good product")
                        .build();

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(reviewRepository.findById(100L))
                .thenReturn(Optional.of(review));

        when(reviewRepository.save(review))
                .thenReturn(review);

        ReviewResponse result =
                reviewService.updateReview(
                        100L,
                        updateRequest
                );

        assertNotNull(result);
        assertEquals(4, result.getRating());
        assertEquals(
                "Very good product",
                result.getComment()
        );

        assertEquals(4, review.getRating());
        assertEquals(
                "Very good product",
                review.getComment()
        );

        verify(reviewRepository).save(review);
    }

    @Test
    void updateReview_shouldThrowWhenReviewDoesNotExist() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(reviewRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> reviewService.updateReview(
                                99L,
                                request
                        )
                );

        assertEquals(
                "Review not found",
                exception.getMessage()
        );

        verify(reviewRepository, never())
                .save(any(Review.class));
    }

    @Test
    void updateReview_shouldThrowWhenUserIsNotOwner() {

        review.setUser(anotherUser);

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(reviewRepository.findById(100L))
                .thenReturn(Optional.of(review));

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> reviewService.updateReview(
                                100L,
                                request
                        )
                );

        assertEquals(
                "You are not allowed to update this review",
                exception.getMessage()
        );

        verify(reviewRepository, never())
                .save(any(Review.class));
    }

    @Test
    void deleteReview_shouldDeleteReviewSuccessfully() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(reviewRepository.findById(100L))
                .thenReturn(Optional.of(review));

        reviewService.deleteReview(100L);

        verify(reviewRepository).delete(review);
    }

    @Test
    void deleteReview_shouldThrowWhenReviewDoesNotExist() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(reviewRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> reviewService.deleteReview(99L)
                );

        assertEquals(
                "Review not found",
                exception.getMessage()
        );

        verify(reviewRepository, never())
                .delete(any(Review.class));
    }

    @Test
    void deleteReview_shouldThrowWhenUserIsNotOwner() {

        review.setUser(anotherUser);

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(reviewRepository.findById(100L))
                .thenReturn(Optional.of(review));

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> reviewService.deleteReview(100L)
                );

        assertEquals(
                "You are not allowed to delete this review",
                exception.getMessage()
        );

        verify(reviewRepository, never())
                .delete(any(Review.class));
    }

    @Test
    void getCurrentUser_shouldFallbackToUsername() {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "shkelqim",
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        when(userRepository.findByEmail("shkelqim"))
                .thenReturn(Optional.empty());

        when(userRepository.findByUsername("shkelqim"))
                .thenReturn(Optional.of(currentUser));

        when(reviewRepository.findByUserId(1L))
                .thenReturn(List.of(review));

        List<ReviewResponse> result =
                reviewService.getMyReviews();

        assertEquals(1, result.size());

        verify(userRepository)
                .findByUsername("shkelqim");
    }

    @Test
    void getCurrentUser_shouldThrowWhenUserDoesNotExist() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.empty());

        when(userRepository.findByUsername(
                "shkelqim@example.com"
        )).thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        reviewService::getMyReviews
                );

        assertEquals(
                "User not found",
                exception.getMessage()
        );

        verifyNoInteractions(reviewRepository);
    }
}