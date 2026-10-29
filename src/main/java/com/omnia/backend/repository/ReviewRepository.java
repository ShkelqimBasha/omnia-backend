package com.omnia.backend.repository;

import com.omnia.backend.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id")
    Optional<Review> findForModeration(@Param("id") Long id);

    List<Review> findByProductId(Long productId);

    List<Review> findByUserId(Long userId);

    List<Review> findAllByOrderByCreatedAtDesc();

    Optional<Review> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Query("""
            select avg(review.rating)
            from Review review
            where review.hidden = false
            """)
    Double findAverageRating();

    @Query("""
            select avg(review.rating)
            from Review review
            where review.hidden = false and review.product.organization.id = :organizationId
            """)
    Double findAverageRatingByOrganizationId(
            @Param("organizationId")
            Long organizationId
    );}