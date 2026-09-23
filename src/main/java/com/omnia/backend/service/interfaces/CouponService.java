package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.CouponRequest;
import com.omnia.backend.dto.response.CouponResponse;

import java.util.List;

public interface CouponService {

    CouponResponse createCoupon(
            CouponRequest request
    );

    List<CouponResponse> getAllCoupons();

    CouponResponse getCouponByCode(
            String code
    );

    void deleteCoupon(
            Long id
    );

    CouponResponse createCouponForOrganization(
            Long organizationId,
            CouponRequest request
    );

    List<CouponResponse> getCouponsForOrganization(
            Long organizationId
    );

    void deleteCouponForOrganization(
            Long organizationId,
            Long couponId
    );
}