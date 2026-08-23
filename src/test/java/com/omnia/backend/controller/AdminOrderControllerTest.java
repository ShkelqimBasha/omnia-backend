package com.omnia.backend.controller;

import com.omnia.backend.dto.response.OrderStatusHistoryResponse;
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.service.interfaces.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminOrderControllerTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        AdminOrderController controller =
                new AdminOrderController(orderService);

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
    void getOrderStatusHistory_ShouldReturnHistory()
            throws Exception {

        LocalDateTime changedAt =
                LocalDateTime.of(
                        2026,
                        8,
                        23,
                        12,
                        0
                );

        OrderStatusHistoryResponse response =
                OrderStatusHistoryResponse.builder()
                        .id(100L)
                        .orderId(90L)
                        .fromStatus(OrderStatus.PENDING)
                        .toStatus(OrderStatus.CONFIRMED)
                        .changedByUserId(1L)
                        .changedByName("shkelqim")
                        .changedAt(changedAt)
                        .build();

        when(
                orderService.getOrderStatusHistoryForAdmin(
                        90L
                )
        ).thenReturn(List.of(response));

        mockMvc.perform(
                        get(
                                "/api/admin/orders/{id}/status-history",
                                90L
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100L))
                .andExpect(jsonPath("$[0].orderId").value(90L))
                .andExpect(
                        jsonPath("$[0].fromStatus")
                                .value("PENDING")
                )
                .andExpect(
                        jsonPath("$[0].toStatus")
                                .value("CONFIRMED")
                )
                .andExpect(
                        jsonPath("$[0].changedByUserId")
                                .value(1L)
                )
                .andExpect(
                        jsonPath("$[0].changedByName")
                                .value("shkelqim")
                )
                .andExpect(
                        jsonPath("$[0].changedAt")
                                .value("2026-08-23T12:00:00")
                );

        verify(orderService)
                .getOrderStatusHistoryForAdmin(90L);
    }
}