package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.CouponRequest;
import com.omnia.backend.dto.response.CouponResponse;
import com.omnia.backend.entity.Coupon;
import com.omnia.backend.enums.CouponStatus;
import com.omnia.backend.mapper.CouponMapper;
import com.omnia.backend.repository.CouponRepository;
import com.omnia.backend.service.interfaces.CouponService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omnia.backend.enums.DiscountType;

import java.math.BigDecimal;
import java.util.Locale;

import java.util.List;
import java.time.LocalDateTime;

@Service
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;

    public CouponServiceImpl(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Override
    @Transactional
    public CouponResponse createCoupon(
            CouponRequest request
    ) {
        String code =
                request.getCode() == null
                        ? ""
                        : request.getCode()
                        .trim()
                        .toUpperCase(Locale.ROOT);

        if (code.isEmpty()) {
            throw new IllegalArgumentException(
                    "Coupon code is required"
            );
        }

        DiscountType discountType =
                request.getDiscountType();

        BigDecimal discountValue =
                request.getDiscountValue();

        if (discountType
                == DiscountType.FREE_SHIPPING
                && discountValue == null) {
            discountValue = BigDecimal.ZERO;
        }

        BigDecimal minimumOrderAmount =
                request.getMinimumOrderAmount() == null
                        ? BigDecimal.ZERO
                        : request.getMinimumOrderAmount();

        validateCouponRules(
                discountType,
                discountValue,
                minimumOrderAmount,
                request.getStartDate(),
                request.getEndDate(),
                request.getUsageLimit(),
                request.getPerUserLimit()
        );

        if (couponRepository
                .existsByCodeIgnoreCase(code)) {
            throw new ResourceAlreadyExistsException(
                    "Coupon already exists"
            );
        }

        Coupon coupon = Coupon.builder()
                .code(code)
                .discountType(discountType)
                .discountValue(discountValue)
                .minimumOrderAmount(
                        minimumOrderAmount
                )
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .usageLimit(request.getUsageLimit())
                .perUserLimit(
                        request.getPerUserLimit()
                )
                .status(CouponStatus.ACTIVE)
                .build();

        Coupon saved =
                couponRepository.save(coupon);

        return CouponMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponResponse> getAllCoupons() {

        return couponRepository.findAll()
                .stream()
                .map(CouponMapper::toResponse)
                .toList();
    }


    @Override
    @Transactional(readOnly = true)
    public CouponResponse getCouponByCode(
            String code
    ) {
        String normalizedCode =
                code == null
                        ? ""
                        : code.trim()
                        .toUpperCase(Locale.ROOT);

        if (normalizedCode.isEmpty()) {
            throw new IllegalArgumentException(
                    "Coupon code is required"
            );
        }

        Coupon coupon =
                couponRepository
                        .findByCodeIgnoreCase(
                                normalizedCode
                        )
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Coupon not found"
                                )
                        );

        return CouponMapper.toResponse(
                coupon
        );
    }

    @Override
    @Transactional
    public void deleteCoupon(
            Long id
    ) {
        Coupon coupon =
                couponRepository.findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Coupon not found"
                                )
                        );

        coupon.setStatus(
                CouponStatus.INACTIVE
        );

        couponRepository.save(
                coupon
        );
    }
    private void validateCouponRules(
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minimumOrderAmount,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Integer usageLimit,
            Integer perUserLimit
    ) {
        if (discountType == null) {
            throw new IllegalArgumentException(
                    "Discount type is required"
            );
        }

        if (discountValue == null) {
            throw new IllegalArgumentException(
                    "Discount value is required"
            );
        }

        if (discountType
                == DiscountType.FREE_SHIPPING) {
            if (discountValue.compareTo(
                    BigDecimal.ZERO
            ) != 0) {
                throw new IllegalArgumentException(
                        "Free shipping discount value must be zero"
                );
            }
        } else if (discountValue.compareTo(
                BigDecimal.ZERO
        ) <= 0) {
            throw new IllegalArgumentException(
                    "Discount value must be positive"
            );
        }

        if (discountType
                == DiscountType.PERCENTAGE
                && discountValue.compareTo(
                new BigDecimal("100")
        ) > 0) {
            throw new IllegalArgumentException(
                    "Percentage discount cannot exceed 100"
            );
        }

        if (minimumOrderAmount.compareTo(
                BigDecimal.ZERO
        ) < 0) {
            throw new IllegalArgumentException(
                    "Minimum order amount cannot be negative"
            );
        }

        if (startDate != null
                && endDate != null
                && !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException(
                    "Coupon end date must be after start date"
            );
        }

        if (usageLimit != null
                && usageLimit <= 0) {
            throw new IllegalArgumentException(
                    "Usage limit must be positive"
            );
        }

        if (perUserLimit != null
                && perUserLimit <= 0) {
            throw new IllegalArgumentException(
                    "Per-user limit must be positive"
            );
        }

        if (usageLimit != null
                && perUserLimit != null
                && perUserLimit > usageLimit) {
            throw new IllegalArgumentException(
                    "Per-user limit cannot exceed usage limit"
            );
        }
    }
}