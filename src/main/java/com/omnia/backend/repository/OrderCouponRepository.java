package com.omnia.backend.repository;

import com.omnia.backend.entity.OrderCoupon;
import com.omnia.backend.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderCouponRepository
        extends JpaRepository<OrderCoupon, Long> {

    @Query("""
            SELECT COUNT(orderCoupon)
            FROM OrderCoupon orderCoupon
            WHERE orderCoupon.coupon.id = :couponId
              AND orderCoupon.order.status <> :excludedStatus
            """)
    long countUsagesExcludingStatus(
            @Param("couponId")
            Long couponId,
            @Param("excludedStatus")
            OrderStatus excludedStatus
    );

    @Query("""
            SELECT COUNT(orderCoupon)
            FROM OrderCoupon orderCoupon
            WHERE orderCoupon.coupon.id = :couponId
              AND orderCoupon.order.user.id = :userId
              AND orderCoupon.order.status <> :excludedStatus
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