package com.omnia.backend.repository;

import com.omnia.backend.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductId(Long productId);

    List<Review> findByUserId(Long userId);

    List<Review> findAllByOrderByCreatedAtDesc();

    Optional<Review> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Query("""
            select avg(review.rating)
            from Review review
            """)
    Double findAverageRating();

    @Query("""
            select avg(review.rating)
            from Review review
            where review.product.organization.id = :organizationId
            """)
    Double findAverageRatingByOrganizationId(
            @Param("organizationId")
            Long organizationId
    );}