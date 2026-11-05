package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.*;
import com.omnia.backend.dto.request.CouponRequest;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.*;
import com.omnia.backend.repository.*;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CouponCoverage35Test {
    CouponRepository coupons;OrganizationRepository companies;OrganizationAccessService access;CouponServiceImpl service;Coupon existing;Organization company;
    @BeforeEach void setup(){
        coupons=mock(CouponRepository.class);companies=mock(OrganizationRepository.class);access=mock(OrganizationAccessService.class);service=new CouponServiceImpl(coupons,companies,access);
        company=Organization.builder().id(10L).status(OrganizationStatus.ACTIVE).build();
        existing=Coupon.builder().id(3L).organization(company).code("OLD").discountType(DiscountType.FIXED).discountValue(BigDecimal.ONE).status(CouponStatus.INACTIVE).build();
        when(coupons.findById(3L)).thenReturn(Optional.of(existing));when(coupons.findByIdAndOrganizationId(3L,10L)).thenReturn(Optional.of(existing));
        when(companies.findById(10L)).thenReturn(Optional.of(company));when(coupons.save(any())).thenAnswer(i->i.getArgument(0));
    }
    CouponRequest valid(){return CouponRequest.builder().code(" promo ").discountType(DiscountType.FIXED).discountValue(BigDecimal.TEN).build();}
    @Test void invalidRulesNeverPersistCoupon(){
        List<Consumer<CouponRequest>> changes=List.of(r->r.setDiscountType(null),r->r.setDiscountValue(null),r->r.setDiscountValue(BigDecimal.ZERO),
            r->r.setDiscountValue(BigDecimal.ONE.negate()),r->r.setMinimumOrderAmount(BigDecimal.ONE.negate()),
            r->{r.setDiscountType(DiscountType.FREE_SHIPPING);r.setDiscountValue(BigDecimal.ONE);},
            r->{r.setDiscountType(DiscountType.PERCENTAGE);r.setDiscountValue(new BigDecimal("101"));},
            r->{r.setStartDate(LocalDateTime.of(2026,1,2,0,0));r.setEndDate(LocalDateTime.of(2026,1,1,0,0));},
            r->r.setUsageLimit(0),r->r.setPerUserLimit(0),r->{r.setUsageLimit(2);r.setPerUserLimit(3);});
        for(var change:changes){var r=valid();change.accept(r);assertThrows(IllegalArgumentException.class,()->service.createCoupon(r));assertThrows(IllegalArgumentException.class,()->service.updateCoupon(3L,r));}
        verify(coupons,never()).save(any());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"})
    void malformedCodesCannotCreateOrUpdate(String code){var r=valid();r.setCode(code);assertThrows(IllegalArgumentException.class,()->service.createCoupon(r));assertThrows(IllegalArgumentException.class,()->service.updateCoupon(3L,r));}
    @Test void nullRequestsAndInvalidIdentitiesFailBeforeWriting(){
        assertThrows(IllegalArgumentException.class,()->service.createCoupon(null));assertThrows(IllegalArgumentException.class,()->service.updateCoupon(3L,null));
        for(Long id:new Long[]{null,0L,-1L}){assertThrows(IllegalArgumentException.class,()->service.activateCoupon(id));assertThrows(IllegalArgumentException.class,()->service.createCouponForOrganization(id,valid()));}
        assertThrows(ResourceNotFoundException.class,()->service.activateCoupon(99L));assertThrows(ResourceNotFoundException.class,()->service.createCouponForOrganization(99L,valid()));
    }
    @Test void freeShippingNormalizesNullValueAndSupportsOptionalLimits(){
        var r=valid();r.setDiscountType(DiscountType.FREE_SHIPPING);r.setDiscountValue(null);r.setMinimumOrderAmount(BigDecimal.ONE);
        r.setStartDate(LocalDateTime.of(2026,1,1,0,0));r.setEndDate(LocalDateTime.of(2027,1,1,0,0));r.setUsageLimit(3);r.setPerUserLimit(2);
        service.createCoupon(r);service.updateCoupon(3L,r);assertEquals(BigDecimal.ZERO,existing.getDiscountValue());assertEquals("PROMO",existing.getCode());assertEquals(2,existing.getPerUserLimit());
    }
    @Test void updatingSameCodePreservesIdentityButOtherCouponCollisionFails(){
        when(coupons.findByCodeIgnoreCase("PROMO")).thenReturn(Optional.of(existing));service.updateCoupon(3L,valid());assertEquals(3L,existing.getId());assertEquals(CouponStatus.INACTIVE,existing.getStatus());assertSame(company,existing.getOrganization());
        when(coupons.findByCodeIgnoreCase("PROMO")).thenReturn(Optional.of(Coupon.builder().id(99L).build()));assertThrows(ResourceAlreadyExistsException.class,()->service.updateCoupon(3L,valid()));
    }
    @Test void reactivationRequiresActiveCompanyAndFutureEndDate(){
        company.setStatus(OrganizationStatus.INACTIVE);assertThrows(IllegalArgumentException.class,()->service.activateCoupon(3L));
        company.setStatus(OrganizationStatus.ACTIVE);existing.setEndDate(LocalDateTime.now().minusDays(1));assertThrows(IllegalArgumentException.class,()->service.activateCoupon(3L));
        existing.setEndDate(LocalDateTime.now().plusDays(1));service.activateCouponForOrganization(10L,3L);assertEquals(CouponStatus.ACTIVE,existing.getStatus());verify(access).requireCanManageCoupons(10L);
        existing.setOrganization(null);existing.setEndDate(null);assertDoesNotThrow(()->service.activateCoupon(3L));
    }
    @Test void organizationCannotReadOrModifyAnotherCompanyCoupon(){
        assertThrows(ResourceNotFoundException.class,()->service.updateCouponForOrganization(11L,3L,valid()));assertThrows(ResourceNotFoundException.class,()->service.activateCouponForOrganization(11L,3L));
        verify(coupons,never()).save(any());
    }
}
