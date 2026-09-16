package com.omnia.backend.repository;

import com.omnia.backend.entity.CheckoutGroupCoupon;
import com.omnia.backend.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckoutGroupCouponRepository
        extends JpaRepository<CheckoutGroupCoupon, Long> {

    @Query("""
            SELECT COUNT(checkoutCoupon)
            FROM CheckoutGroupCoupon checkoutCoupon
            WHERE checkoutCoupon.coupon.id = :couponId
              AND EXISTS (
                    SELECT orderRecord.id
                    FROM Order orderRecord
                    WHERE orderRecord.checkoutGroup =
                          checkoutCoupon.checkoutGroup
                      AND orderRecord.status <> :excludedStatus
              )
            """)
    long countUsagesExcludingStatus(
            @Param("couponId")
            Long couponId,
            @Param("excludedStatus")
            OrderStatus excludedStatus
    );

    @Query("""
            SELECT COUNT(checkoutCoupon)
            FROM CheckoutGroupCoupon checkoutCoupon
            WHERE checkoutCoupon.coupon.id = :couponId
              AND checkoutCoupon.checkoutGroup.user.id = :userId
              AND EXISTS (
                    SELECT orderRecord.id
                    FROM Order orderRecord
                    WHERE orderRecord.checkoutGroup =
                          checkoutCoupon.checkoutGroup
                      AND orderRecord.status <> :excludedStatus
              )
            """)
    long countUserUsagesExcludingStatus(
            @Param("couponId")
            Long couponId,
            @Param("userId")
            Long userId,
            @Param("excludedStatus")
            OrderStatus excludedStatus
    );
}