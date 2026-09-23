package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.CouponResponse;
import com.omnia.backend.entity.Coupon;
import com.omnia.backend.entity.Organization;

public final class CouponMapper {

    public static CouponResponse toResponse(
            Coupon coupon
    ) {
        Organization organization =
                coupon.getOrganization();

        return CouponResponse.builder()
                .id(coupon.getId())
                .organizationId(
                        organization == null
                                ? null
                                : organization.getId()
                )
                .organizationName(
                        organization == null
                                ? null
                                : organization.getName()
                )
                .code(coupon.getCode())
                .discountType(
                        coupon.getDiscountType()
                )
                .discountValue(
                        coupon.getDiscountValue()
                )
                .minimumOrderAmount(
                        coupon.getMinimumOrderAmount()
                )
                .startDate(coupon.getStartDate())
                .endDate(coupon.getEndDate())
                .usageLimit(coupon.getUsageLimit())
                .perUserLimit(
                        coupon.getPerUserLimit()
                )
                .status(coupon.getStatus())
                .build();
    }

    private CouponMapper() {
    }
}