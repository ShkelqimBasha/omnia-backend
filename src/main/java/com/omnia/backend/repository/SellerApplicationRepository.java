package com.omnia.backend.repository;

import com.omnia.backend.entity.SellerApplication;
import com.omnia.backend.enums.SellerApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface SellerApplicationRepository extends JpaRepository<SellerApplication, Long> {

    boolean existsByUserIdAndStatus(Long userId, SellerApplicationStatus status);

    Optional<SellerApplication> findFirstByUserIdOrderByIdDesc(Long userId);

    Page<SellerApplication> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<SellerApplication> findByStatusOrderByCreatedAtDescIdDesc(SellerApplicationStatus status, Pageable pageable);

    /** Holds the row so two admins cannot approve the same application twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SellerApplication a where a.id = :id")
    Optional<SellerApplication> findForReview(@Param("id") Long id);
}
