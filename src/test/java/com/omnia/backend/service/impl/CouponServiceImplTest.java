package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.CouponRequest;
import com.omnia.backend.dto.response.CouponResponse;
import com.omnia.backend.entity.Coupon;
import com.omnia.backend.enums.CouponStatus;
import com.omnia.backend.enums.DiscountType;
import com.omnia.backend.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private CouponServiceImpl couponService;

    private CouponRequest request;
    private Coupon coupon;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @BeforeEach
    void setUp() {

        startDate = LocalDateTime.of(
                2026,
                7,
                16,
                0,
                0
        );

        endDate = LocalDateTime.of(
                2026,
                8,
                16,
                23,
                59
        );

        request = CouponRequest.builder()
                .code("SUMMER20")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00"))
                .minimumOrderAmount(
                        new BigDecimal("50.00")
                )
                .startDate(startDate)
                .endDate(endDate)
                .usageLimit(100)
                .perUserLimit(2)
                .build();

        coupon = Coupon.builder()
                .id(1L)
                .code("SUMMER20")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00"))
                .minimumOrderAmount(
                        new BigDecimal("50.00")
                )
                .startDate(startDate)
                .endDate(endDate)
                .usageLimit(100)
                .perUserLimit(2)
                .status(CouponStatus.ACTIVE)
                .build();
    }

    @Test
    void createCoupon_shouldCreateCouponSuccessfully() {

        when(couponRepository.existsByCodeIgnoreCase("SUMMER20"))
                .thenReturn(false);

        when(couponRepository.save(any(Coupon.class)))
                .thenReturn(coupon);

        CouponResponse result =
                couponService.createCoupon(request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("SUMMER20", result.getCode());
        assertEquals(
                DiscountType.PERCENTAGE,
                result.getDiscountType()
        );
        assertEquals(
                new BigDecimal("20.00"),
                result.getDiscountValue()
        );
        assertEquals(
                new BigDecimal("50.00"),
                result.getMinimumOrderAmount()
        );

        assertEquals(
                2,
                result.getPerUserLimit()
        );
        assertEquals(startDate, result.getStartDate());
        assertEquals(endDate, result.getEndDate());
        assertEquals(100, result.getUsageLimit());
        assertEquals(
                CouponStatus.ACTIVE,
                result.getStatus()
        );

        ArgumentCaptor<Coupon> couponCaptor =
                ArgumentCaptor.forClass(Coupon.class);

        verify(couponRepository)
                .save(couponCaptor.capture());

        Coupon savedCoupon =
                couponCaptor.getValue();

        assertNull(savedCoupon.getId());
        assertEquals("SUMMER20", savedCoupon.getCode());
        assertEquals(
                DiscountType.PERCENTAGE,
                savedCoupon.getDiscountType()
        );
        assertEquals(
                new BigDecimal("20.00"),
                savedCoupon.getDiscountValue()
        );
        assertEquals(
                new BigDecimal("50.00"),
                savedCoupon.getMinimumOrderAmount()
        );

        assertEquals(
                2,
                savedCoupon.getPerUserLimit()
        );

        assertEquals(startDate, savedCoupon.getStartDate());
        assertEquals(endDate, savedCoupon.getEndDate());
        assertEquals(100, savedCoupon.getUsageLimit());
        assertEquals(
                CouponStatus.ACTIVE,
                savedCoupon.getStatus()
        );

        verify(couponRepository)
                .existsByCodeIgnoreCase("SUMMER20");
    }

    @Test
    void createCoupon_shouldThrowWhenCouponAlreadyExists() {

        when(couponRepository.existsByCodeIgnoreCase("SUMMER20"))
                .thenReturn(true);

        ResourceAlreadyExistsException exception =
                assertThrows(
                        ResourceAlreadyExistsException.class,
                        () -> couponService.createCoupon(request)
                );

        assertEquals(
                "Coupon already exists",
                exception.getMessage()
        );

        verify(couponRepository, never())
                .save(any(Coupon.class));
    }

    @Test
    void getAllCoupons_shouldReturnCoupons() {

        Coupon secondCoupon = Coupon.builder()
                .id(2L)
                .code("FIXED10")
                .discountType(DiscountType.FIXED)
                .discountValue(new BigDecimal("10.00"))
                .startDate(startDate)
                .endDate(endDate)
                .usageLimit(50)
                .status(CouponStatus.ACTIVE)
                .build();

        when(couponRepository.findAll())
                .thenReturn(List.of(coupon, secondCoupon));

        List<CouponResponse> result =
                couponService.getAllCoupons();

        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals(
                "SUMMER20",
                result.get(0).getCode()
        );

        assertEquals(
                "FIXED10",
                result.get(1).getCode()
        );

        assertEquals(
                DiscountType.FIXED,
                result.get(1).getDiscountType()
        );

        verify(couponRepository).findAll();
    }

    @Test
    void getAllCoupons_shouldReturnEmptyList() {

        when(couponRepository.findAll())
                .thenReturn(List.of());

        List<CouponResponse> result =
                couponService.getAllCoupons();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(couponRepository).findAll();
    }

    @Test
    void getCouponByCode_shouldReturnCoupon() {

        when(couponRepository.findByCodeIgnoreCase("SUMMER20"))
                .thenReturn(Optional.of(coupon));

        CouponResponse result =
                couponService.getCouponByCode("SUMMER20");

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("SUMMER20", result.getCode());
        assertEquals(
                CouponStatus.ACTIVE,
                result.getStatus()
        );

        verify(couponRepository)
                .findByCodeIgnoreCase("SUMMER20");
    }

    @Test
    void getCouponByCode_shouldThrowWhenCouponDoesNotExist() {

        when(couponRepository.findByCodeIgnoreCase("INVALID"))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> couponService.getCouponByCode(
                                "INVALID"
                        )
                );

        assertEquals(
                "Coupon not found",
                exception.getMessage()
        );
    }

    @Test
    void deleteCoupon_shouldDeactivateCouponSuccessfully() {

        when(couponRepository.findById(1L))
                .thenReturn(
                        Optional.of(coupon)
                );

        when(couponRepository.save(coupon))
                .thenReturn(coupon);

        couponService.deleteCoupon(1L);

        assertEquals(
                CouponStatus.INACTIVE,
                coupon.getStatus()
        );

        verify(couponRepository)
                .save(coupon);

        verify(couponRepository, never())
                .delete(any(Coupon.class));
    }

    @Test
    void deleteCoupon_shouldThrowWhenCouponDoesNotExist() {

        when(couponRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> couponService.deleteCoupon(99L)
                );

        assertEquals(
                "Coupon not found",
                exception.getMessage()
        );

        verify(couponRepository, never())
                .save(any(Coupon.class));
    }
}