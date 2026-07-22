package com.omnia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnia.backend.dto.request.ReviewRequest;
import com.omnia.backend.dto.response.ReviewResponse;
import com.omnia.backend.service.interfaces.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    private static final Long PRODUCT_ID = 10L;
    private static final Long REVIEW_ID = 20L;
    private static final Long USER_ID = 30L;

    @Mock
    private ReviewService reviewService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        ReviewController controller =
                new ReviewController(reviewService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        objectMapper = new ObjectMapper()
                .findAndRegisterModules();
    }

    @Test
    void createReview_WithValidRequest_ShouldReturnCreated()
            throws Exception {

        ReviewRequest request = createRequest();

        ReviewResponse response = createResponse(
                REVIEW_ID,
                PRODUCT_ID
        );

        when(
                reviewService.createReview(
                        org.mockito.ArgumentMatchers.eq(PRODUCT_ID),
                        org.mockito.ArgumentMatchers.any(ReviewRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post("/api/reviews/{productId}", PRODUCT_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.id").value(REVIEW_ID))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.username").value("review-user"))
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.productName").value("Test Product"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Excellent product"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        ArgumentCaptor<ReviewRequest> requestCaptor =
                ArgumentCaptor.forClass(ReviewRequest.class);

        verify(reviewService).createReview(
                org.mockito.ArgumentMatchers.eq(PRODUCT_ID),
                requestCaptor.capture()
        );

        assertThat(requestCaptor.getValue().getRating())
                .isEqualTo(5);

        assertThat(requestCaptor.getValue().getComment())
                .isEqualTo("Excellent product");

        verifyNoMoreInteractions(reviewService);
    }

    @Test
    void getProductReviews_ShouldReturnReviews()
            throws Exception {

        ReviewResponse firstReview =
                createResponse(20L, PRODUCT_ID);

        ReviewResponse secondReview =
                createResponse(21L, PRODUCT_ID);

        secondReview.setRating(4);
        secondReview.setComment("Very good");

        when(
                reviewService.getProductReviews(PRODUCT_ID)
        ).thenReturn(
                List.of(firstReview, secondReview)
        );

        mockMvc.perform(
                        get(
                                "/api/reviews/product/{productId}",
                                PRODUCT_ID
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(20L))
                .andExpect(
                        jsonPath("$[0].productId")
                                .value(PRODUCT_ID)
                )
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[1].id").value(21L))
                .andExpect(jsonPath("$[1].rating").value(4))
                .andExpect(
                        jsonPath("$[1].comment")
                                .value("Very good")
                );

        verify(reviewService)
                .getProductReviews(PRODUCT_ID);

        verifyNoMoreInteractions(reviewService);
    }

    @Test
    void getMyReviews_ShouldReturnCurrentUserReviews()
            throws Exception {

        ReviewResponse response =
                createResponse(REVIEW_ID, PRODUCT_ID);

        when(reviewService.getMyReviews())
                .thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/reviews/my")
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(
                        jsonPath("$[0].id")
                                .value(REVIEW_ID)
                )
                .andExpect(
                        jsonPath("$[0].userId")
                                .value(USER_ID)
                )
                .andExpect(
                        jsonPath("$[0].username")
                                .value("review-user")
                )
                .andExpect(
                        jsonPath("$[0].productId")
                                .value(PRODUCT_ID)
                );

        verify(reviewService).getMyReviews();

        verifyNoMoreInteractions(reviewService);
    }

    @Test
    void updateReview_WithValidRequest_ShouldReturnUpdatedReview()
            throws Exception {

        ReviewRequest request = ReviewRequest.builder()
                .rating(4)
                .comment("Updated review")
                .build();

        ReviewResponse response =
                createResponse(REVIEW_ID, PRODUCT_ID);

        response.setRating(4);
        response.setComment("Updated review");

        when(
                reviewService.updateReview(
                        org.mockito.ArgumentMatchers.eq(REVIEW_ID),
                        org.mockito.ArgumentMatchers.any(ReviewRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        put("/api/reviews/{reviewId}", REVIEW_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.id").value(REVIEW_ID))
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(
                        jsonPath("$.comment")
                                .value("Updated review")
                );

        ArgumentCaptor<ReviewRequest> requestCaptor =
                ArgumentCaptor.forClass(ReviewRequest.class);

        verify(reviewService).updateReview(
                org.mockito.ArgumentMatchers.eq(REVIEW_ID),
                requestCaptor.capture()
        );

        assertThat(requestCaptor.getValue().getRating())
                .isEqualTo(4);

        assertThat(requestCaptor.getValue().getComment())
                .isEqualTo("Updated review");

        verifyNoMoreInteractions(reviewService);
    }

    @Test
    void deleteReview_ShouldReturnNoContent()
            throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/reviews/{reviewId}",
                                REVIEW_ID
                        )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(reviewService).deleteReview(REVIEW_ID);

        verifyNoMoreInteractions(reviewService);
    }

    private ReviewRequest createRequest() {

        return ReviewRequest.builder()
                .rating(5)
                .comment("Excellent product")
                .build();
    }

    private ReviewResponse createResponse(
            Long reviewId,
            Long productId
    ) {

        return ReviewResponse.builder()
                .id(reviewId)
                .userId(USER_ID)
                .username("review-user")
                .productId(productId)
                .productName("Test Product")
                .rating(5)
                .comment("Excellent product")
                .createdAt(
                        LocalDateTime.of(
                                2026,
                                7,
                                22,
                                12,
                                30
                        )
                )
                .build();
    }
}