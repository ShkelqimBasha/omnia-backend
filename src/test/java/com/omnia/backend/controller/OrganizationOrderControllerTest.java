package com.omnia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.service.interfaces.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationOrderControllerTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrganizationOrderController controller =
                new OrganizationOrderController(
                        orderService
                );

        ObjectMapper objectMapper =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .disable(
                                SerializationFeature
                                        .WRITE_DATES_AS_TIMESTAMPS
                        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(
                                objectMapper
                        )
                )
                .build();
    }

    @Test
    void getOrders_ShouldUseOrganizationScope()
            throws Exception {
        when(
                orderService.getOrdersForOrganization(
                        12L
                )
        ).thenReturn(List.of());

        mockMvc.perform(
                        get(
                                "/api/organizations/{organizationId}/orders",
                                12L
                        )
                )
                .andExpect(status().isOk());

        verify(orderService)
                .getOrdersForOrganization(12L);
    }

    @Test
    void getStatusHistory_ShouldUseOrganizationAndOrder()
            throws Exception {
        when(
                orderService
                        .getOrderStatusHistoryForOrganization(
                                12L,
                                90L
                        )
        ).thenReturn(List.of());

        mockMvc.perform(
                        get(
                                "/api/organizations/{organizationId}/orders/{orderId}/status-history",
                                12L,
                                90L
                        )
                )
                .andExpect(status().isOk());

        verify(orderService)
                .getOrderStatusHistoryForOrganization(
                        12L,
                        90L
                );
    }

    @Test
    void updateStatus_ShouldUseOrganizationAndOrder()
            throws Exception {
        mockMvc.perform(
                        patch(
                                "/api/organizations/{organizationId}/orders/{orderId}/status",
                                12L,
                                90L
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                        {
                                          "status": "CONFIRMED"
                                        }
                                        """
                                )
                )
                .andExpect(status().isOk());

        verify(orderService)
                .updateOrderStatusForOrganization(
                        12L,
                        90L,
                        OrderStatus.CONFIRMED
                );
    }
}