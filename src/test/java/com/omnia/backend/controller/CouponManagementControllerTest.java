package com.omnia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnia.backend.dto.request.CouponRequest;
import com.omnia.backend.dto.response.CouponResponse;
import com.omnia.backend.enums.DiscountType;
import com.omnia.backend.service.interfaces.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CouponManagementControllerTest {
    @Mock CouponService service;
    MockMvc mvc;
    ObjectMapper json;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new CouponController(service), new OrganizationCouponController(service)).build();
        json = new ObjectMapper().findAndRegisterModules();
    }
    private String body() throws Exception {
        return json.writeValueAsString(CouponRequest.builder().code("TEST10")
                .discountType(DiscountType.PERCENTAGE).discountValue(BigDecimal.TEN).build());
    }
    @Test void organizationUpdateUsesScopedService() throws Exception {
        when(service.updateCouponForOrganization(eq(10L), eq(5L), any()))
                .thenReturn(CouponResponse.builder().id(5L).code("TEST10").build());
        mvc.perform(put("/api/organizations/10/coupons/5").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("TEST10"));
        verify(service).updateCouponForOrganization(eq(10L), eq(5L), any());
        verifyNoMoreInteractions(service);
    }
    @Test void platformUpdateUsesPlatformService() throws Exception {
        when(service.updateCoupon(eq(5L), any())).thenReturn(CouponResponse.builder().id(5L).build());
        mvc.perform(put("/api/coupons/5").contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isOk());
        verify(service).updateCoupon(eq(5L), any());
        verifyNoMoreInteractions(service);
    }
    @Test void organizationActivateUsesScopedService() throws Exception {
        when(service.activateCouponForOrganization(10L, 5L)).thenReturn(CouponResponse.builder().id(5L).build());
        mvc.perform(post("/api/organizations/10/coupons/5/activate")).andExpect(status().isOk());
        verify(service).activateCouponForOrganization(10L, 5L);
        verifyNoMoreInteractions(service);
    }
    @Test void platformActivateUsesPlatformService() throws Exception {
        when(service.activateCoupon(5L)).thenReturn(CouponResponse.builder().id(5L).build());
        mvc.perform(post("/api/coupons/5/activate")).andExpect(status().isOk());
        verify(service).activateCoupon(5L);
        verifyNoMoreInteractions(service);
    }
}
