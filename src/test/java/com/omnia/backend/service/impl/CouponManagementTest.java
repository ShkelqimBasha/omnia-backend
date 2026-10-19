package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.CouponRequest;
import com.omnia.backend.entity.Coupon;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.enums.CouponStatus;
import com.omnia.backend.enums.DiscountType;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.repository.CouponRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponManagementTest {
    @Mock CouponRepository coupons;
    @Mock OrganizationRepository organizations;
    @Mock OrganizationAccessService access;
    CouponServiceImpl service;
    Coupon coupon;
    CouponRequest request;

    @BeforeEach void setup() {
        service = new CouponServiceImpl(coupons, organizations, access);
        coupon = Coupon.builder().id(5L).code("STORE10").discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN).minimumOrderAmount(BigDecimal.ZERO)
                .status(CouponStatus.INACTIVE).organization(Organization.builder().id(10L)
                        .name("Store").status(OrganizationStatus.ACTIVE).build()).build();
        request = CouponRequest.builder().code(" store20 ").discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20")).usageLimit(100).perUserLimit(2).build();
    }

    @Test void updatePreservesIdentityCompanyAndStatus() {
        when(coupons.findByIdAndOrganizationId(5L, 10L)).thenReturn(Optional.of(coupon));
        when(coupons.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.updateCouponForOrganization(10L, 5L, request);
        assertEquals("STORE20", result.getCode());
        assertEquals(5L, result.getId());
        assertEquals(10L, result.getOrganizationId());
        assertEquals(CouponStatus.INACTIVE, coupon.getStatus());
        assertEquals(BigDecimal.ZERO, coupon.getMinimumOrderAmount());
        verify(access).requireCanManageCoupons(10L);
        verify(coupons, never()).findById(any());
    }

    @Test void updateCannotReachAnotherCompany() {
        assertThrows(ResourceNotFoundException.class, () -> service.updateCouponForOrganization(20L, 5L, request));
        verify(access).requireCanManageCoupons(20L);
        verify(coupons, never()).save(any());
        verify(coupons, never()).findById(any());
    }

    @Test void updateRejectsUnauthorizedBeforeReadingCoupon() {
        doThrow(new AccessDeniedException("Denied")).when(access).requireCanManageCoupons(10L);
        assertThrows(AccessDeniedException.class, () -> service.updateCouponForOrganization(10L, 5L, request));
        verifyNoInteractions(coupons);
    }

    @Test void duplicateCodeDoesNotMutateExistingCoupon() {
        when(coupons.findById(5L)).thenReturn(Optional.of(coupon));
        when(coupons.findByCodeIgnoreCase("STORE20")).thenReturn(Optional.of(Coupon.builder().id(6L).build()));
        assertThrows(ResourceAlreadyExistsException.class, () -> service.updateCoupon(5L, request));
        assertEquals("STORE10", coupon.getCode());
        verify(coupons, never()).save(any());
    }

    @Test void sameCodeCanBeSaved() {
        request.setCode("store10");
        when(coupons.findById(5L)).thenReturn(Optional.of(coupon));
        when(coupons.findByCodeIgnoreCase("STORE10")).thenReturn(Optional.of(coupon));
        when(coupons.save(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals("STORE10", service.updateCoupon(5L, request).getCode());
    }

    @Test void invalidPercentageIsRejectedWithoutMutation() {
        request.setDiscountValue(new BigDecimal("101"));
        when(coupons.findById(5L)).thenReturn(Optional.of(coupon));
        assertThrows(IllegalArgumentException.class, () -> service.updateCoupon(5L, request));
        assertEquals(BigDecimal.TEN, coupon.getDiscountValue());
        verify(coupons, never()).save(any());
    }

    @Test void invalidDatesAreRejected() {
        request.setStartDate(LocalDateTime.now());
        request.setEndDate(request.getStartDate().minusDays(1));
        when(coupons.findById(5L)).thenReturn(Optional.of(coupon));
        assertThrows(IllegalArgumentException.class, () -> service.updateCoupon(5L, request));
        verify(coupons, never()).save(any());
    }

    @Test void freeShippingCanUseNullValue() {
        request.setDiscountType(DiscountType.FREE_SHIPPING);
        request.setDiscountValue(null);
        when(coupons.findById(5L)).thenReturn(Optional.of(coupon));
        when(coupons.save(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals(BigDecimal.ZERO, service.updateCoupon(5L, request).getDiscountValue());
    }

    @Test void activationRequiresOrganizationAndMatchingCoupon() {
        when(coupons.findByIdAndOrganizationId(5L, 10L)).thenReturn(Optional.of(coupon));
        when(coupons.save(any())).thenAnswer(i -> i.getArgument(0));
        service.activateCouponForOrganization(10L, 5L);
        assertEquals(CouponStatus.ACTIVE, coupon.getStatus());
        verify(access).requireCanManageCoupons(10L);
        verify(coupons, never()).findById(any());
    }

    @Test void activationCannotReachAnotherCompany() {
        assertThrows(ResourceNotFoundException.class, () -> service.activateCouponForOrganization(20L, 5L));
        verify(coupons, never()).save(any());
    }

    @Test void activationRejectsUnauthorizedBeforeReadingCoupon() {
        doThrow(new AccessDeniedException("Denied")).when(access).requireCanManageCoupons(10L);
        assertThrows(AccessDeniedException.class, () -> service.activateCouponForOrganization(10L, 5L));
        verifyNoInteractions(coupons);
    }

    @Test void expiredCouponCannotBeReactivated() {
        coupon.setEndDate(LocalDateTime.now().minusDays(1));
        when(coupons.findById(5L)).thenReturn(Optional.of(coupon));
        assertThrows(IllegalArgumentException.class, () -> service.activateCoupon(5L));
        assertEquals(CouponStatus.INACTIVE, coupon.getStatus());
        verify(coupons, never()).save(any());
    }

    @Test void inactiveCompanyCannotReactivateCoupon() {
        coupon.getOrganization().setStatus(OrganizationStatus.INACTIVE);
        when(coupons.findById(5L)).thenReturn(Optional.of(coupon));
        assertThrows(IllegalArgumentException.class, () -> service.activateCoupon(5L));
        verify(coupons, never()).save(any());
    }
}
