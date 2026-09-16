package com.omnia.backend.controller;

import com.omnia.backend.dto.response.CheckoutResponse;
import com.omnia.backend.service.interfaces.CheckoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CheckoutControllerTest {

    @Mock
    private CheckoutService checkoutService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new CheckoutController(
                                checkoutService
                        )
                )
                .build();
    }

    @Test
    void checkout_WithValidRequest_ShouldReturnCreated()
            throws Exception {

        CheckoutResponse response =
                CheckoutResponse.builder()
                        .checkoutReference(
                                "checkout-reference-123"
                        )
                        .userId(1L)
                        .orderCount(2)
                        .subtotalAmount(
                                new BigDecimal("60.00")
                        )
                        .shippingFee(
                                new BigDecimal("7.00")
                        )
                        .discountAmount(
                                new BigDecimal("6.00")
                        )
                        .totalAmount(
                                new BigDecimal("61.00")
                        )
                        .couponCode("OMNIA10")
                        .orders(List.of())
                        .build();

        when(
                checkoutService.checkout(any())
        ).thenReturn(response);

        mockMvc.perform(
                        post("/api/orders/checkout")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "shippingName":
                                            "Shkelqim Basha",
                                          "shippingEmail":
                                            "shkelqim@example.com",
                                          "shippingPhone":
                                            "+355690000000",
                                          "shippingAddress":
                                            "Tirane, Shqiperi",
                                          "couponCode":
                                            "OMNIA10",
                                          "items": [
                                            {
                                              "productId": 10,
                                              "quantity": 1
                                            },
                                            {
                                              "productId": 20,
                                              "quantity": 1
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath(
                                "$.checkoutReference"
                        ).value(
                                "checkout-reference-123"
                        )
                )
                .andExpect(
                        jsonPath("$.orderCount")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.totalAmount")
                                .value(61.0)
                );

        verify(
                checkoutService,
                times(1)
        ).checkout(any());
    }

    @Test
    void checkout_WithoutItems_ShouldReturnBadRequest()
            throws Exception {

        mockMvc.perform(
                        post("/api/orders/checkout")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "shippingName":
                                            "Shkelqim Basha",
                                          "shippingEmail":
                                            "shkelqim@example.com",
                                          "shippingPhone":
                                            "+355690000000",
                                          "shippingAddress":
                                            "Tirane, Shqiperi",
                                          "items": []
                                        }
                                        """)
                )
                .andExpect(
                        status().isBadRequest()
                );

        verifyNoInteractions(checkoutService);
    }
}