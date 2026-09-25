package com.omnia.backend.controller;

import com.omnia.backend.dto.response.OrganizationStatisticsResponse;
import com.omnia.backend.service.impl.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StatisticsControllerTest {

    @Mock
    private StatisticsService statisticsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new OrganizationStatisticsController(
                                statisticsService
                        ),
                        new AdminStatisticsController(
                                statisticsService
                        )
                )
                .build();
    }

    @Test
    void organizationEndpointShouldReturnScopedStatistics()
            throws Exception {

        when(statisticsService
                .getOrganizationStatistics(10L, 7))
                .thenReturn(createResponse());

        mockMvc.perform(
                        get(
                                "/api/organizations/{organizationId}/statistics",
                                10L
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.totalProducts")
                                .value(8)
                )
                .andExpect(
                        jsonPath("$.activeProducts")
                                .value(6)
                )
                .andExpect(
                        jsonPath("$.lowStockProducts")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.totalOrders")
                                .value(4)
                )
                .andExpect(
                        jsonPath("$.activeOrders")
                                .value(3)
                )
                .andExpect(
                        jsonPath("$.totalRevenue")
                                .value(125.50)
                );

        verify(statisticsService)
                .getOrganizationStatistics(10L, 7);
    }

    @Test
    void adminEndpointShouldReturnPlatformStatistics()
            throws Exception {

        when(statisticsService
                .getPlatformStatistics(7))
                .thenReturn(createResponse());

        mockMvc.perform(
                        get("/api/admin/statistics")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.totalProducts")
                                .value(8)
                )
                .andExpect(
                        jsonPath("$.totalOrders")
                                .value(4)
                )
                .andExpect(
                        jsonPath("$.totalRevenue")
                                .value(125.50)
                );

        verify(statisticsService)
                .getPlatformStatistics(7);
    }

    @Test
    void adminEndpointShouldForwardRequestedPeriod()
            throws Exception {

        when(statisticsService
                .getPlatformStatistics(30))
                .thenReturn(createResponse());

        mockMvc.perform(
                        get("/api/admin/statistics")
                                .param("days", "30")
                )
                .andExpect(status().isOk());

        verify(statisticsService)
                .getPlatformStatistics(30);
    }
    private OrganizationStatisticsResponse
    createResponse() {
        return OrganizationStatisticsResponse.builder()
                .totalProducts(8L)
                .activeProducts(6L)
                .lowStockProducts(2L)
                .totalOrders(4L)
                .activeOrders(3L)
                .totalRevenue(
                        new BigDecimal("125.50")
                )
                .build();
    }
}