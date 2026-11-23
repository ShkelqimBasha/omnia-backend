package com.omnia.backend.repository;

import com.omnia.backend.entity.ProductReport;
import com.omnia.backend.enums.ProductReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ProductReportRepository extends JpaRepository<ProductReport, Long> {

    long countByReporterIdAndCreatedAtAfter(Long reporterId, LocalDateTime since);

    boolean existsByReporterIdAndProductIdAndStatus(Long reporterId, Long productId, ProductReportStatus status);

    List<ProductReport> findByProductIdAndStatus(Long productId, ProductReportStatus status);

    @EntityGraph(attributePaths = {"product", "organization", "reporter", "resolvedBy"})
    Page<ProductReport> findByStatusOrderByIdDesc(ProductReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "organization", "reporter", "resolvedBy"})
    Page<ProductReport> findAllByOrderByIdDesc(Pageable pageable);

    @Query("select r.product.id, count(r) from ProductReport r "
            + "where r.status = :status and r.product.id in :ids group by r.product.id")
    List<Object[]> countByProducts(@Param("ids") Collection<Long> productIds,
                                   @Param("status") ProductReportStatus status);
}
