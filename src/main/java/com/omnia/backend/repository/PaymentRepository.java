package com.omnia.backend.repository;

import com.omnia.backend.entity.Payment;
import com.omnia.backend.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    @Query("""
            select coalesce(sum(payment.order.totalAmount), 0)
            from Payment payment
            where payment.status = :status
              and payment.paidAt >= :startDate
              and payment.paidAt < :endDate
            """)
    BigDecimal sumRevenueByStatusAndPaidAtBetween(
            @Param("status")
            PaymentStatus status,
            @Param("startDate")
            LocalDateTime startDate,
            @Param("endDate")
            LocalDateTime endDate
    );

    @Query("""
            select coalesce(sum(payment.order.totalAmount), 0)
            from Payment payment
            where payment.order.organization.id = :organizationId
              and payment.status = :status
              and payment.paidAt >= :startDate
              and payment.paidAt < :endDate
            """)
    BigDecimal
    sumRevenueByOrganizationIdAndStatusAndPaidAtBetween(
            @Param("organizationId")
            Long organizationId,
            @Param("status")
            PaymentStatus status,
            @Param("startDate")
            LocalDateTime startDate,
            @Param("endDate")
            LocalDateTime endDate
    );

    @Query("""
            select payment.method, count(payment)
            from Payment payment
            where payment.status = :status
            group by payment.method
            order by count(payment) desc, payment.method asc
            """)
    List<Object[]> findPaymentMethodUsageByStatus(
            @Param("status")
            PaymentStatus status
    );

    @Query("""
            select payment.method, count(payment)
            from Payment payment
            where payment.order.organization.id = :organizationId
              and payment.status = :status
            group by payment.method
            order by count(payment) desc, payment.method asc
            """)
    List<Object[]>
    findPaymentMethodUsageByOrganizationIdAndStatus(
            @Param("organizationId")
            Long organizationId,
            @Param("status")
            PaymentStatus status
    );}