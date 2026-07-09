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

import java.util.List;

@Service
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;

    public CouponServiceImpl(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Override
    @Transactional
    public CouponResponse createCoupon(CouponRequest request) {

        if (couponRepository.existsByCode(request.getCode())) {
            throw new ResourceAlreadyExistsException("Coupon already exists");
        }

        Coupon coupon = Coupon.builder()
                .code(request.getCode())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .usageLimit(request.getUsageLimit())
                .status(CouponStatus.ACTIVE)
                .build();

        Coupon saved = couponRepository.save(coupon);

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
    public CouponResponse getCouponByCode(String code) {

        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Coupon not found"));

        return CouponMapper.toResponse(coupon);
    }

    @Override
    @Transactional
    public void deleteCoupon(Long id) {

        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Coupon not found"));

        couponRepository.delete(coupon);
    }
}