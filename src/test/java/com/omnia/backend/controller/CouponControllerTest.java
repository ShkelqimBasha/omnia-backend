package com.omnia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnia.backend.dto.request.CouponRequest;
import com.omnia.backend.dto.response.CouponResponse;
import com.omnia.backend.enums.CouponStatus;
import com.omnia.backend.enums.DiscountType;
import com.omnia.backend.service.interfaces.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CouponControllerTest {

    private static final Long COUPON_ID = 10L;
    private static final String COUPON_CODE = "SUMMER20";

    private static final LocalDateTime START_DATE =
            LocalDateTime.of(
                    2026,
                    7,
                    1,
                    0,
                    0
            );

    private static final LocalDateTime END_DATE =
            LocalDateTime.of(
                    2026,
                    8,
                    31,
                    23,
                    59
            );

    @Mock
    private CouponService couponService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        CouponController controller =
                new CouponController(couponService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        objectMapper = new ObjectMapper()
                .findAndRegisterModules();
    }

    @Test
    void createCoupon_WithValidRequest_ShouldReturnCreated()
            throws Exception {

        CouponRequest request =
                createRequest(
                        COUPON_CODE,
                        DiscountType.PERCENTAGE,
                        "20.00",
                        100
                );

        CouponResponse response =
                createResponse(
                        COUPON_ID,
                        COUPON_CODE,
                        DiscountType.PERCENTAGE,
                        "20.00",
                        100,
                        CouponStatus.ACTIVE
                );

        when(
                couponService.createCoupon(
                        any(CouponRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post("/api/coupons")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(COUPON_ID)
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(COUPON_CODE)
                )
                .andExpect(
                        jsonPath("$.discountType")
                                .value("PERCENTAGE")
                )
                .andExpect(
                        jsonPath("$.discountValue")
                                .value(20.00)
                )
                .andExpect(
                        jsonPath("$.startDate")
                                .isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.endDate")
                                .isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.usageLimit")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE")
                );

        ArgumentCaptor<CouponRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        CouponRequest.class
                );

        verify(couponService).createCoupon(
                requestCaptor.capture()
        );

        CouponRequest capturedRequest =
                requestCaptor.getValue();

        assertThat(capturedRequest.getCode())
                .isEqualTo(COUPON_CODE);

        assertThat(capturedRequest.getDiscountType())
                .isEqualTo(DiscountType.PERCENTAGE);

        assertThat(capturedRequest.getDiscountValue())
                .isEqualByComparingTo("20.00");

        assertThat(capturedRequest.getStartDate())
                .isEqualTo(START_DATE);

        assertThat(capturedRequest.getEndDate())
                .isEqualTo(END_DATE);

        assertThat(capturedRequest.getUsageLimit())
                .isEqualTo(100);

        verifyNoMoreInteractions(couponService);
    }

    @Test
    void getAllCoupons_ShouldReturnCoupons()
            throws Exception {

        CouponResponse percentageCoupon =
                createResponse(
                        10L,
                        "SUMMER20",
                        DiscountType.PERCENTAGE,
                        "20.00",
                        100,
                        CouponStatus.ACTIVE
                );

        CouponResponse fixedCoupon =
                createResponse(
                        11L,
                        "SAVE10",
                        DiscountType.FIXED,
                        "10.00",
                        50,
                        CouponStatus.INACTIVE
                );

        when(couponService.getAllCoupons())
                .thenReturn(
                        List.of(
                                percentageCoupon,
                                fixedCoupon
                        )
                );

        mockMvc.perform(
                        get("/api/coupons")
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$").isArray())
                .andExpect(
                        jsonPath("$.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$[0].id")
                                .value(10L)
                )
                .andExpect(
                        jsonPath("$[0].code")
                                .value("SUMMER20")
                )
                .andExpect(
                        jsonPath("$[0].discountType")
                                .value("PERCENTAGE")
                )
                .andExpect(
                        jsonPath("$[0].status")
                                .value("ACTIVE")
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(11L)
                )
                .andExpect(
                        jsonPath("$[1].code")
                                .value("SAVE10")
                )
                .andExpect(
                        jsonPath("$[1].discountType")
                                .value("FIXED")
                )
                .andExpect(
                        jsonPath("$[1].discountValue")
                                .value(10.00)
                )
                .andExpect(
                        jsonPath("$[1].status")
                                .value("INACTIVE")
                );

        verify(couponService).getAllCoupons();
        verifyNoMoreInteractions(couponService);
    }

    @Test
    void getCouponByCode_ShouldReturnCoupon()
            throws Exception {

        CouponResponse response =
                createResponse(
                        COUPON_ID,
                        COUPON_CODE,
                        DiscountType.PERCENTAGE,
                        "20.00",
                        100,
                        CouponStatus.ACTIVE
                );

        when(
                couponService.getCouponByCode(
                        COUPON_CODE
                )
        ).thenReturn(response);

        mockMvc.perform(
                        get(
                                "/api/coupons/{code}",
                                COUPON_CODE
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(COUPON_ID)
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(COUPON_CODE)
                )
                .andExpect(
                        jsonPath("$.discountType")
                                .value("PERCENTAGE")
                )
                .andExpect(
                        jsonPath("$.discountValue")
                                .value(20.00)
                )
                .andExpect(
                        jsonPath("$.usageLimit")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE")
                );

        verify(couponService)
                .getCouponByCode(COUPON_CODE);

        verifyNoMoreInteractions(couponService);
    }

    @Test
    void deleteCoupon_ShouldReturnNoContent()
            throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/coupons/{id}",
                                COUPON_ID
                        )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(couponService)
                .deleteCoupon(COUPON_ID);

        verifyNoMoreInteractions(couponService);
    }

    private CouponRequest createRequest(
            String code,
            DiscountType discountType,
            String discountValue,
            Integer usageLimit
    ) {

        return CouponRequest.builder()
                .code(code)
                .discountType(discountType)
                .discountValue(
                        new BigDecimal(discountValue)
                )
                .startDate(START_DATE)
                .endDate(END_DATE)
                .usageLimit(usageLimit)
                .build();
    }

    private CouponResponse createResponse(
            Long id,
            String code,
            DiscountType discountType,
            String discountValue,
            Integer usageLimit,
            CouponStatus status
    ) {

        return CouponResponse.builder()
                .id(id)
                .code(code)
                .discountType(discountType)
                .discountValue(
                        new BigDecimal(discountValue)
                )
                .startDate(START_DATE)
                .endDate(END_DATE)
                .usageLimit(usageLimit)
                .status(status)
                .build();
    }
}