package com.omnia.backend.repository;

import com.omnia.backend.entity.ProductInquiry;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.InquiryStatus;
import com.omnia.backend.enums.InquiryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductInquiryRepository extends JpaRepository<ProductInquiry, Long> {

    Page<ProductInquiry> findByOrganizationIdOrderByIdDesc(Long organizationId, Pageable pageable);

    Page<ProductInquiry> findByOrganizationIdAndStatusOrderByIdDesc(Long organizationId, InquiryStatus status,
                                                                    Pageable pageable);

    Page<ProductInquiry> findBySenderIdOrderByIdDesc(Long senderId, Pageable pageable);

    @Query(value = "SELECT COUNT(*) FROM product_inquiries WHERE sender_id = :senderId "
            + "AND product_id = :productId AND created_at > NOW() - INTERVAL 1 DAY", nativeQuery = true)
    long countRecent(@Param("senderId") Long senderId, @Param("productId") Long productId);

    /** Platform admin: every company's inquiries, each filter optional. */
    @Query(value = "select i from ProductInquiry i join i.product p join p.category c "
            + "where (:status is null or i.status = :status) "
            + "and (:type is null or i.type = :type) "
            + "and (:kind is null or c.kind = :kind) "
            + "and (:organizationId is null or i.organization.id = :organizationId) "
            + "order by i.id desc",
            countQuery = "select count(i) from ProductInquiry i join i.product p join p.category c "
                    + "where (:status is null or i.status = :status) "
                    + "and (:type is null or i.type = :type) "
                    + "and (:kind is null or c.kind = :kind) "
                    + "and (:organizationId is null or i.organization.id = :organizationId)")
    Page<ProductInquiry> search(@Param("status") InquiryStatus status,
                                @Param("type") InquiryType type,
                                @Param("kind") CategoryKind kind,
                                @Param("organizationId") Long organizationId,
                                Pageable pageable);
}
