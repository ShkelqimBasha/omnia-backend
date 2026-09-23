package com.omnia.backend.repository;

import com.omnia.backend.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponRepository
        extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(
            String code
    );

    Optional<Coupon> findByCodeIgnoreCase(
            String code
    );

    List<Coupon>
    findAllByOrganizationIdOrderByCodeAsc(
            Long organizationId
    );

    Optional<Coupon>
    findByIdAndOrganizationId(
            Long id,
            Long organizationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT coupon
            FROM Coupon coupon
            WHERE UPPER(coupon.code) = UPPER(:code)
            """)
    Optional<Coupon> findByCodeForUpdate(
            @Param("code")
            String code
    );

    boolean existsByCode(
            String code
    );

    boolean existsByCodeIgnoreCase(
            String code
    );
}