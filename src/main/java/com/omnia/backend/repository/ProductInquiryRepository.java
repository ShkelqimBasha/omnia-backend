package com.omnia.backend.repository;

import com.omnia.backend.entity.ProductInquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductInquiryRepository extends JpaRepository<ProductInquiry, Long> {

    Page<ProductInquiry> findByOrganizationIdOrderByIdDesc(Long organizationId, Pageable pageable);

    Page<ProductInquiry> findBySenderIdOrderByIdDesc(Long senderId, Pageable pageable);

    @Query(value = "SELECT COUNT(*) FROM product_inquiries WHERE sender_id = :senderId "
            + "AND product_id = :productId AND created_at > NOW() - INTERVAL 1 DAY", nativeQuery = true)
    long countRecent(@Param("senderId") Long senderId, @Param("productId") Long productId);
}
