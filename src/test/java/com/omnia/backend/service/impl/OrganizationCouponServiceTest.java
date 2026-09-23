package com.omnia.backend.service.impl;

import com.omnia.backend.dto.request.CouponRequest;
import com.omnia.backend.dto.response.CouponResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationCouponServiceTest {

    private static final long ORGANIZATION_ID = 10L;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationAccessService
            organizationAccessService;

    private CouponServiceImpl couponService;
    private Organization organization;
    private CouponRequest request;

    @BeforeEach
    void setUp() {
        couponService = new CouponServiceImpl(
                couponRepository,
                organizationRepository,
                organizationAccessService
        );

        organization = Organization.builder()
                .id(ORGANIZATION_ID)
                .name("Omnia Store")
                .slug("omnia-store")
                .status(OrganizationStatus.ACTIVE)
                .build();

        request = CouponRequest.builder()
                .code("store10")
                .discountType(
                        DiscountType.PERCENTAGE
                )
                .discountValue(
                        new BigDecimal("10.00")
                )
                .minimumOrderAmount(
                        new BigDecimal("20.00")
                )
                .usageLimit(100)
                .perUserLimit(2)
                .build();
    }

    @Test
    void createCoupon_ShouldAssignOrganization() {
        when(organizationRepository
                .findById(ORGANIZATION_ID))
                .thenReturn(
                        Optional.of(organization)
                );

        when(couponRepository
                .existsByCodeIgnoreCase("STORE10"))
                .thenReturn(false);

        when(couponRepository
                .save(any(Coupon.class)))
                .thenAnswer(invocation -> {
                    Coupon coupon =
                            invocation.getArgument(0);
                    coupon.setId(50L);
                    return coupon;
                });

        CouponResponse response =
                couponService
                        .createCouponForOrganization(
                                ORGANIZATION_ID,
                                request
                        );

        assertEquals(50L, response.getId());
        assertEquals(
                ORGANIZATION_ID,
                response.getOrganizationId()
        );
        assertEquals(
                "Omnia Store",
                response.getOrganizationName()
        );
        assertEquals("STORE10", response.getCode());

        ArgumentCaptor<Coupon> captor =
                ArgumentCaptor.forClass(
                        Coupon.class
                );

        verify(couponRepository)
                .save(captor.capture());

        assertSame(
                organization,
                captor.getValue()
                        .getOrganization()
        );

        verify(organizationAccessService)
                .requireCanManageCoupons(
                        ORGANIZATION_ID
                );
    }

    @Test
    void getCoupons_ShouldReturnOnlyOrganizationCoupons() {
        Coupon coupon = Coupon.builder()
                .id(50L)
                .organization(organization)
                .code("STORE10")
                .discountType(
                        DiscountType.PERCENTAGE
                )
                .discountValue(
                        new BigDecimal("10.00")
                )
                .minimumOrderAmount(
                        BigDecimal.ZERO
                )
                .status(CouponStatus.ACTIVE)
                .build();

        when(organizationRepository
                .findById(ORGANIZATION_ID))
                .thenReturn(
                        Optional.of(organization)
                );

        when(couponRepository
                .findAllByOrganizationIdOrderByCodeAsc(
                        ORGANIZATION_ID
                ))
                .thenReturn(List.of(coupon));

        List<CouponResponse> responses =
                couponService
                        .getCouponsForOrganization(
                                ORGANIZATION_ID
                        );

        assertEquals(1, responses.size());
        assertEquals(
                ORGANIZATION_ID,
                responses.get(0)
                        .getOrganizationId()
        );

        verify(organizationAccessService)
                .requireCanAccessOrganization(
                        ORGANIZATION_ID
                );

        verify(couponRepository)
                .findAllByOrganizationIdOrderByCodeAsc(
                        ORGANIZATION_ID
                );
    }

    @Test
    void deleteCoupon_ShouldRequireMatchingOrganization() {
        Coupon coupon = Coupon.builder()
                .id(50L)
                .organization(organization)
                .code("STORE10")
                .discountType(
                        DiscountType.PERCENTAGE
                )
                .discountValue(
                        new BigDecimal("10.00")
                )
                .minimumOrderAmount(
                        BigDecimal.ZERO
                )
                .status(CouponStatus.ACTIVE)
                .build();

        when(couponRepository
                .findByIdAndOrganizationId(
                        50L,
                        ORGANIZATION_ID
                ))
                .thenReturn(Optional.of(coupon));

        couponService
                .deleteCouponForOrganization(
                        ORGANIZATION_ID,
                        50L
                );

        assertEquals(
                CouponStatus.INACTIVE,
                coupon.getStatus()
        );

        verify(organizationAccessService)
                .requireCanManageCoupons(
                        ORGANIZATION_ID
                );

        verify(couponRepository)
                .save(coupon);
    }
}