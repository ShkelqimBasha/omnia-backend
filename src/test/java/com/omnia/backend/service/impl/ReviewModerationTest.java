package com.omnia.backend.service.impl;

import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.Review;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.ReviewRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewModerationTest {
    @Mock ReviewRepository reviews;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @InjectMocks ReviewServiceImpl service;
    private User admin;
    private Review review;
    @BeforeEach void setup(){
        admin=User.builder().id(1L).username("admin").email("admin@test.com").build();
        review=Review.builder().id(3L).user(admin).product(Product.builder().id(9L).name("Product").build()).rating(5).comment("Original").build();
        auth("ROLE_SUPER_ADMIN");
    }
    private void auth(String authority){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@test.com",null,AuthorityUtils.createAuthorityList(authority)));}
    private void adminExists(){when(users.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));}
    private void found(){adminExists();when(reviews.findForModeration(3L)).thenReturn(Optional.of(review));when(reviews.save(review)).thenReturn(review);}
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void hidingPreservesTextRatingAndReply(){found();review.setAdminReply("Existing reply");var result=service.setReviewVisibility(3L,true);assertTrue(result.isHidden());assertEquals("Original",result.getComment());assertEquals(5,result.getRating());assertEquals("Existing reply",result.getAdminReply());verify(reviews,never()).delete(any());}
    @Test void publishingRestoresVisibility(){found();review.setHidden(true);assertFalse(service.setReviewVisibility(3L,false).isHidden());}
    @Test void replyIsTrimmedAndAttributedToAuthenticatedAdmin(){found();var result=service.replyToReview(3L,"  Thank you  ");assertEquals("Thank you",result.getAdminReply());assertEquals("admin",result.getRepliedBy());assertNotNull(result.getRepliedAt());assertEquals("Original",result.getComment());}
    @Test void blankReplyIsRejected(){adminExists();assertThrows(IllegalArgumentException.class,()->service.replyToReview(3L,"   "));verifyNoInteractions(reviews);}
    @Test void nullReplyIsRejected(){adminExists();assertThrows(IllegalArgumentException.class,()->service.replyToReview(3L,null));verifyNoInteractions(reviews);}
    @Test void replyAboveLimitIsRejected(){adminExists();assertThrows(IllegalArgumentException.class,()->service.replyToReview(3L,"x".repeat(2001)));verifyNoInteractions(reviews);}
    @Test void exactReplyLimitAccepted(){found();assertEquals(2000,service.replyToReview(3L,"x".repeat(2000)).getAdminReply().length());}
    @Test void removingReplyClearsAttributionButKeepsReview(){found();review.setAdminReply("Reply");review.setRepliedBy("old");review.setRepliedAt(java.time.LocalDateTime.now());var result=service.removeReviewReply(3L);assertNull(result.getAdminReply());assertNull(result.getRepliedBy());assertNull(result.getRepliedAt());assertEquals("Original",result.getComment());}
    @Test void missingReviewReturnsNotFound(){adminExists();when(reviews.findForModeration(77L)).thenReturn(Optional.empty());assertThrows(ResourceNotFoundException.class,()->service.setReviewVisibility(77L,true));verify(reviews,never()).save(any());}
    @Test void platformAdminCannotModerate(){auth("ROLE_ADMIN");assertThrows(AccessDeniedException.class,()->service.setReviewVisibility(3L,true));assertThrows(AccessDeniedException.class,()->service.replyToReview(3L,"reply"));assertThrows(AccessDeniedException.class,()->service.removeReviewReply(3L));verifyNoInteractions(reviews,users);}
    @Test void unauthenticatedCannotModerate(){SecurityContextHolder.clearContext();assertThrows(AccessDeniedException.class,()->service.setReviewVisibility(3L,true));verifyNoInteractions(reviews,users);}
    @Test void publicFeedOmitsHiddenReviewAndIncludesReply(){when(products.existsById(9L)).thenReturn(true);Review hidden=Review.builder().id(4L).user(admin).product(review.getProduct()).rating(1).hidden(true).build();review.setAdminReply("Thanks");when(reviews.findByProductId(9L)).thenReturn(List.of(hidden,review));var result=service.getProductReviews(9L);assertEquals(1,result.size());assertEquals(3L,result.getFirst().getId());assertEquals("Thanks",result.getFirst().getAdminReply());}
    @Test void ownerCanStillSeeTheirHiddenReview(){adminExists();review.setHidden(true);when(reviews.findByUserId(1L)).thenReturn(List.of(review));assertTrue(service.getMyReviews().getFirst().isHidden());}
}
