package com.omnia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnia.backend.dto.request.PaymentRequest;
import com.omnia.backend.dto.response.PaymentResponse;
import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.enums.PaymentStatus;
import com.omnia.backend.service.interfaces.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final Long PAYMENT_ID = 10L;
    private static final Long ORDER_ID = 20L;
    private static final String TRANSACTION_ID =
            "transaction-123";

    private static final LocalDateTime PAID_AT =
            LocalDateTime.of(
                    2026,
                    7,
                    22,
                    12,
                    30
            );

    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(
                    2026,
                    7,
                    22,
                    12,
                    25
            );

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        PaymentController controller =
                new PaymentController(paymentService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        objectMapper = new ObjectMapper()
                .findAndRegisterModules();
    }

    @Test
    void createPayment_WithValidRequest_ShouldReturnCreated()
            throws Exception {

        PaymentRequest request =
                PaymentRequest.builder()
                        .orderId(ORDER_ID)
                        .method(PaymentMethod.CARD)
                        .build();

        PaymentResponse response =
                createResponse(
                        PaymentMethod.CARD,
                        PaymentStatus.SUCCESS
                );

        when(
                paymentService.createPayment(
                        any(PaymentRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post("/api/payments")
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
                                .value(PAYMENT_ID)
                )
                .andExpect(
                        jsonPath("$.orderId")
                                .value(ORDER_ID)
                )
                .andExpect(
                        jsonPath("$.method")
                                .value("CARD")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("SUCCESS")
                )
                .andExpect(
                        jsonPath("$.transactionId")
                                .value(TRANSACTION_ID)
                )
                .andExpect(
                        jsonPath("$.paidAt")
                                .isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.createdAt")
                                .isNotEmpty()
                );

        ArgumentCaptor<PaymentRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        PaymentRequest.class
                );

        verify(paymentService).createPayment(
                requestCaptor.capture()
        );

        PaymentRequest capturedRequest =
                requestCaptor.getValue();

        assertThat(capturedRequest.getOrderId())
                .isEqualTo(ORDER_ID);

        assertThat(capturedRequest.getMethod())
                .isEqualTo(PaymentMethod.CARD);

        verifyNoMoreInteractions(paymentService);
    }

    @Test
    void getPaymentByOrder_ShouldReturnPayment()
            throws Exception {

        PaymentResponse response =
                createResponse(
                        PaymentMethod.PAYPAL,
                        PaymentStatus.PENDING
                );

        when(
                paymentService.getPaymentByOrder(
                        ORDER_ID
                )
        ).thenReturn(response);

        mockMvc.perform(
                        get(
                                "/api/payments/order/{orderId}",
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
                                .value(PAYMENT_ID)
                )
                .andExpect(
                        jsonPath("$.orderId")
                                .value(ORDER_ID)
                )
                .andExpect(
                        jsonPath("$.method")
                                .value("PAYPAL")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PENDING")
                )
                .andExpect(
                        jsonPath("$.transactionId")
                                .value(TRANSACTION_ID)
                )
                .andExpect(
                        jsonPath("$.paidAt")
                                .isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.createdAt")
                                .isNotEmpty()
                );

        verify(paymentService)
                .getPaymentByOrder(ORDER_ID);

        verifyNoMoreInteractions(paymentService);
    }

    private PaymentResponse createResponse(
            PaymentMethod method,
            PaymentStatus status
    ) {

        return PaymentResponse.builder()
                .id(PAYMENT_ID)
                .orderId(ORDER_ID)
                .method(method)
                .status(status)
                .transactionId(TRANSACTION_ID)
                .paidAt(PAID_AT)
                .createdAt(CREATED_AT)
                .build();
    }
}