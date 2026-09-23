package com.omnia.backend.repository;

import com.omnia.backend.entity.Payment;
import com.omnia.backend.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface PaymentRepository
        extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(
            Long orderId
    );

    @Query("""
            select coalesce(sum(p.order.totalAmount), 0)
            from Payment p
            where p.order.organization.id = :organizationId
              and p.status = :status
            """)
    BigDecimal sumRevenueByOrganizationIdAndStatus(
            @Param("organizationId")
            Long organizationId,
            @Param("status")
            PaymentStatus status
    );

    @Query("""
            select coalesce(sum(p.order.totalAmount), 0)
            from Payment p
            where p.status = :status
            """)
    BigDecimal sumRevenueByStatus(
            @Param("status")
            PaymentStatus status
    );
}