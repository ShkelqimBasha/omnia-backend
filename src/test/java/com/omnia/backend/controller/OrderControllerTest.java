package com.omnia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnia.backend.dto.request.CreateOrderItemRequest;
import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.dto.response.OrderItemResponse;
import com.omnia.backend.dto.response.OrderResponse;
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.service.interfaces.OrderService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private static final Long ORDER_ID = 10L;
    private static final Long USER_ID = 20L;
    private static final Long ADDRESS_ID = 30L;
    private static final Long PRODUCT_ID = 40L;

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        OrderController controller =
                new OrderController(orderService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        objectMapper = new ObjectMapper()
                .findAndRegisterModules();
    }

    @Test
    void createOrder_WithValidRequest_ShouldReturnCreated()
            throws Exception {

        CreateOrderRequest request =
                createRequest(2);

        OrderResponse response =
                createResponse(
                        ORDER_ID,
                        OrderStatus.PENDING,
                        2
                );

        when(
                orderService.createOrder(
                        any(CreateOrderRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post("/api/orders")
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
                                .value(ORDER_ID)
                )
                .andExpect(
                        jsonPath("$.userId")
                                .value(USER_ID)
                )
                .andExpect(
                        jsonPath("$.addressId")
                                .value(ADDRESS_ID)
                )
                .andExpect(
                        jsonPath("$.totalAmount")
                                .value(49.98)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PENDING")
                )
                .andExpect(
                        jsonPath("$.createdAt")
                                .isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.items")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.items[0].productId")
                                .value(PRODUCT_ID)
                )
                .andExpect(
                        jsonPath("$.items[0].productName")
                                .value("Test Product")
                )
                .andExpect(
                        jsonPath("$.items[0].productImage")
                                .value("/api/files/50")
                )
                .andExpect(
                        jsonPath("$.items[0].variantInfo")
                                .value("Color: Black")
                )
                .andExpect(
                        jsonPath("$.items[0].unitPrice")
                                .value(24.99)
                )
                .andExpect(
                        jsonPath("$.items[0].quantity")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.items[0].subtotal")
                                .value(49.98)
                );

        ArgumentCaptor<CreateOrderRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        CreateOrderRequest.class
                );

        verify(orderService).createOrder(
                requestCaptor.capture()
        );

        CreateOrderRequest capturedRequest =
                requestCaptor.getValue();

        assertThat(capturedRequest.getAddressId())
                .isEqualTo(ADDRESS_ID);

        assertThat(capturedRequest.getItems())
                .hasSize(1);

        assertThat(
                capturedRequest
                        .getItems()
                        .getFirst()
                        .getProductId()
        ).isEqualTo(PRODUCT_ID);

        assertThat(
                capturedRequest
                        .getItems()
                        .getFirst()
                        .getQuantity()
        ).isEqualTo(2);

        verifyNoMoreInteractions(orderService);
    }

    @Test
    void createOrder_WithEmptyItems_ShouldReturnBadRequest()
            throws Exception {

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .addressId(ADDRESS_ID)
                        .items(List.of())
                        .build();

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                orderService,
                never()
        ).createOrder(any(CreateOrderRequest.class));

        verifyNoInteractions(orderService);
    }

    @Test
    void createOrder_WithInvalidItemQuantity_ShouldReturnBadRequest()
            throws Exception {

        CreateOrderRequest request =
                createRequest(0);

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                orderService,
                never()
        ).createOrder(any(CreateOrderRequest.class));

        verifyNoInteractions(orderService);
    }

    @Test
    void getMyOrders_ShouldReturnOrders()
            throws Exception {

        OrderResponse firstOrder =
                createResponse(
                        10L,
                        OrderStatus.PENDING,
                        2
                );

        OrderResponse secondOrder =
                createResponse(
                        11L,
                        OrderStatus.SHIPPED,
                        1
                );

        when(orderService.getMyOrders())
                .thenReturn(
                        List.of(
                                firstOrder,
                                secondOrder
                        )
                );

        mockMvc.perform(
                        get("/api/orders/my")
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
                        jsonPath("$[0].status")
                                .value("PENDING")
                )
                .andExpect(
                        jsonPath("$[0].items[0].quantity")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(11L)
                )
                .andExpect(
                        jsonPath("$[1].status")
                                .value("SHIPPED")
                )
                .andExpect(
                        jsonPath("$[1].items[0].quantity")
                                .value(1)
                );

        verify(orderService).getMyOrders();
        verifyNoMoreInteractions(orderService);
    }

    @Test
    void getOrderById_ShouldReturnOrder()
            throws Exception {

        OrderResponse response =
                createResponse(
                        ORDER_ID,
                        OrderStatus.CONFIRMED,
                        2
                );

        when(
                orderService.getOrderById(ORDER_ID)
        ).thenReturn(response);

        mockMvc.perform(
                        get(
                                "/api/orders/{id}",
                                ORDER_ID
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
                                .value(ORDER_ID)
                )
                .andExpect(
                        jsonPath("$.userId")
                                .value(USER_ID)
                )
                .andExpect(
                        jsonPath("$.addressId")
                                .value(ADDRESS_ID)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("CONFIRMED")
                )
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.items[0].productId")
                                .value(PRODUCT_ID)
                );

        verify(orderService)
                .getOrderById(ORDER_ID);

        verifyNoMoreInteractions(orderService);
    }

    private CreateOrderRequest createRequest(
            int quantity
    ) {

        CreateOrderItemRequest item =
                CreateOrderItemRequest.builder()
                        .productId(PRODUCT_ID)
                        .quantity(quantity)
                        .build();

        return CreateOrderRequest.builder()
                .addressId(ADDRESS_ID)
                .items(List.of(item))
                .build();
    }

    private OrderResponse createResponse(
            Long orderId,
            OrderStatus status,
            int quantity
    ) {

        BigDecimal unitPrice =
                new BigDecimal("24.99");

        BigDecimal subtotal =
                unitPrice.multiply(
                        BigDecimal.valueOf(quantity)
                );

        OrderItemResponse item =
                OrderItemResponse.builder()
                        .productId(PRODUCT_ID)
                        .productName("Test Product")
                        .productImage("/api/files/50")
                        .variantInfo("Color: Black")
                        .unitPrice(unitPrice)
                        .quantity(quantity)
                        .subtotal(subtotal)
                        .build();

        return OrderResponse.builder()
                .id(orderId)
                .userId(USER_ID)
                .addressId(ADDRESS_ID)
                .totalAmount(subtotal)
                .status(status)
                .createdAt(
                        LocalDateTime.of(
                                2026,
                                7,
                                22,
                                12,
                                30
                        )
                )
                .items(List.of(item))
                .build();
    }
}