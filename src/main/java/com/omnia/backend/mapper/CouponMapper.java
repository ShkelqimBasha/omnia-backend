    package com.omnia.backend.mapper;

    import com.omnia.backend.dto.response.CouponResponse;
    import com.omnia.backend.entity.Coupon;

    public class CouponMapper {

        public static CouponResponse toResponse(Coupon coupon) {
            return CouponResponse.builder()
                    .id(coupon.getId())
                    .code(coupon.getCode())
                    .discountType(coupon.getDiscountType())
                    .discountValue(coupon.getDiscountValue())
                    .startDate(coupon.getStartDate())
                    .endDate(coupon.getEndDate())
                    .usageLimit(coupon.getUsageLimit())
                    .status(coupon.getStatus())
                    .build();
        }

        private CouponMapper() {
        }
    }