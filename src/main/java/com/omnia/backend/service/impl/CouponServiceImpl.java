package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.CouponRequest;
import com.omnia.backend.dto.response.CouponResponse;
import com.omnia.backend.entity.Coupon;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.enums.CouponStatus;
import com.omnia.backend.enums.DiscountType;
import com.omnia.backend.mapper.CouponMapper;
import com.omnia.backend.repository.CouponRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.security.service.OrganizationAccessService;
import com.omnia.backend.service.interfaces.CouponService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final OrganizationRepository
            organizationRepository;
    private final OrganizationAccessService
            organizationAccessService;

    public CouponServiceImpl(
            CouponRepository couponRepository,
            OrganizationRepository organizationRepository,
            OrganizationAccessService
                    organizationAccessService
    ) {
        this.couponRepository = couponRepository;
        this.organizationRepository =
                organizationRepository;
        this.organizationAccessService =
                organizationAccessService;
    }

    @Override
    @Transactional
    public CouponResponse createCoupon(
            CouponRequest request
    ) {
        return createCouponInternal(
                request,
                null
        );
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
                normalizeCode(code);

        Coupon coupon =
                couponRepository
                        .findByCodeIgnoreCase(
                                normalizedCode
                        )
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Coupon not found"
                                        )
                        );

        return CouponMapper.toResponse(coupon);
    }

    @Override
    @Transactional
    public void deleteCoupon(
            Long id
    ) {
        Coupon coupon =
                couponRepository.findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Coupon not found"
                                        )
                        );

        deactivateCoupon(coupon);
    }

    @Override
    @Transactional
    public CouponResponse
    createCouponForOrganization(
            Long organizationId,
            CouponRequest request
    ) {
        organizationAccessService
                .requireCanManageCoupons(
                        organizationId
                );

        Organization organization =
                requireOrganization(
                        organizationId
                );

        if (!organization.isActive()) {
            throw new IllegalArgumentException(
                    "Organization is not active"
            );
        }

        return createCouponInternal(
                request,
                organization
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponResponse>
    getCouponsForOrganization(
            Long organizationId
    ) {
        organizationAccessService
                .requireCanAccessOrganization(
                        organizationId
                );

        requireOrganization(organizationId);

        return couponRepository
                .findAllByOrganizationIdOrderByCodeAsc(
                        organizationId
                )
                .stream()
                .map(CouponMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteCouponForOrganization(
            Long organizationId,
            Long couponId
    ) {
        organizationAccessService
                .requireCanManageCoupons(
                        organizationId
                );

        Coupon coupon =
                couponRepository
                        .findByIdAndOrganizationId(
                                couponId,
                                organizationId
                        )
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Coupon not found"
                                        )
                        );

        deactivateCoupon(coupon);
    }

    private CouponResponse createCouponInternal(
            CouponRequest request,
            Organization organization
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Coupon request is required"
            );
        }

        String code =
                normalizeCode(
                        request.getCode()
                );

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
                .organization(organization)
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

    private Organization requireOrganization(
            Long organizationId
    ) {
        if (organizationId == null
                || organizationId <= 0L) {
            throw new IllegalArgumentException(
                    "Organization id must be positive"
            );
        }

        return organizationRepository
                .findById(organizationId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Organization not found"
                                )
                );
    }

    private void deactivateCoupon(
            Coupon coupon
    ) {
        coupon.setStatus(
                CouponStatus.INACTIVE
        );

        couponRepository.save(coupon);
    }

    private String normalizeCode(
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

        return normalizedCode;
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