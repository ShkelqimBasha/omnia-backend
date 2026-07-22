package com.omnia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnia.backend.dto.request.AddToCartRequest;
import com.omnia.backend.dto.request.UpdateCartItemRequest;
import com.omnia.backend.dto.response.CartItemResponse;
import com.omnia.backend.dto.response.CartResponse;
import com.omnia.backend.service.interfaces.CartService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    private static final Long CART_ID = 10L;
    private static final Long USER_ID = 20L;
    private static final Long ITEM_ID = 30L;
    private static final Long PRODUCT_ID = 40L;
    private static final Long VARIANT_ID = 50L;

    @Mock
    private CartService cartService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        CartController controller =
                new CartController(cartService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        objectMapper = new ObjectMapper()
                .findAndRegisterModules();
    }

    @Test
    void getCart_ShouldReturnCart()
            throws Exception {

        when(cartService.getCart())
                .thenReturn(createCartResponse(2));

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.id").value(CART_ID))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(
                        jsonPath("$.items[0].id")
                                .value(ITEM_ID)
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
                        jsonPath("$.items[0].variantId")
                                .value(VARIANT_ID)
                )
                .andExpect(
                        jsonPath("$.items[0].quantity")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.items[0].price")
                                .value(24.99)
                )
                .andExpect(
                        jsonPath("$.items[0].subtotal")
                                .value(49.98)
                )
                .andExpect(jsonPath("$.total").value(49.98));

        verify(cartService).getCart();
        verifyNoMoreInteractions(cartService);
    }

    @Test
    void addToCart_WithValidRequest_ShouldReturnUpdatedCart()
            throws Exception {

        AddToCartRequest request =
                AddToCartRequest.builder()
                        .productId(PRODUCT_ID)
                        .variantId(VARIANT_ID)
                        .quantity(2)
                        .build();

        when(
                cartService.addToCart(
                        any(AddToCartRequest.class)
                )
        ).thenReturn(createCartResponse(2));

        mockMvc.perform(
                        post("/api/cart")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.id").value(CART_ID))
                .andExpect(
                        jsonPath("$.items[0].productId")
                                .value(PRODUCT_ID)
                )
                .andExpect(
                        jsonPath("$.items[0].variantId")
                                .value(VARIANT_ID)
                )
                .andExpect(
                        jsonPath("$.items[0].quantity")
                                .value(2)
                )
                .andExpect(jsonPath("$.total").value(49.98));

        ArgumentCaptor<AddToCartRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        AddToCartRequest.class
                );

        verify(cartService).addToCart(
                requestCaptor.capture()
        );

        assertThat(requestCaptor.getValue().getProductId())
                .isEqualTo(PRODUCT_ID);

        assertThat(requestCaptor.getValue().getVariantId())
                .isEqualTo(VARIANT_ID);

        assertThat(requestCaptor.getValue().getQuantity())
                .isEqualTo(2);

        verifyNoMoreInteractions(cartService);
    }

    @Test
    void addToCart_WithoutProductId_ShouldReturnBadRequest()
            throws Exception {

        AddToCartRequest request =
                AddToCartRequest.builder()
                        .productId(null)
                        .variantId(VARIANT_ID)
                        .quantity(2)
                        .build();

        mockMvc.perform(
                        post("/api/cart")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                cartService,
                never()
        ).addToCart(any(AddToCartRequest.class));

        verifyNoInteractions(cartService);
    }

    @Test
    void addToCart_WithInvalidQuantity_ShouldReturnBadRequest()
            throws Exception {

        AddToCartRequest request =
                AddToCartRequest.builder()
                        .productId(PRODUCT_ID)
                        .variantId(VARIANT_ID)
                        .quantity(0)
                        .build();

        mockMvc.perform(
                        post("/api/cart")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    void updateItem_WithValidRequest_ShouldReturnUpdatedCart()
            throws Exception {

        UpdateCartItemRequest request =
                UpdateCartItemRequest.builder()
                        .quantity(3)
                        .build();

        when(
                cartService.updateItem(
                        eq(ITEM_ID),
                        any(UpdateCartItemRequest.class)
                )
        ).thenReturn(createCartResponse(3));

        mockMvc.perform(
                        put("/api/cart/{itemId}", ITEM_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.id").value(CART_ID))
                .andExpect(
                        jsonPath("$.items[0].id")
                                .value(ITEM_ID)
                )
                .andExpect(
                        jsonPath("$.items[0].quantity")
                                .value(3)
                )
                .andExpect(
                        jsonPath("$.items[0].subtotal")
                                .value(74.97)
                )
                .andExpect(jsonPath("$.total").value(74.97));

        ArgumentCaptor<UpdateCartItemRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        UpdateCartItemRequest.class
                );

        verify(cartService).updateItem(
                eq(ITEM_ID),
                requestCaptor.capture()
        );

        assertThat(requestCaptor.getValue().getQuantity())
                .isEqualTo(3);

        verifyNoMoreInteractions(cartService);
    }

    @Test
    void updateItem_WithInvalidQuantity_ShouldReturnBadRequest()
            throws Exception {

        UpdateCartItemRequest request =
                UpdateCartItemRequest.builder()
                        .quantity(0)
                        .build();

        mockMvc.perform(
                        put("/api/cart/{itemId}", ITEM_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                cartService,
                never()
        ).updateItem(
                eq(ITEM_ID),
                any(UpdateCartItemRequest.class)
        );

        verifyNoInteractions(cartService);
    }

    @Test
    void removeItem_ShouldReturnNoContent()
            throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/cart/{itemId}",
                                ITEM_ID
                        )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(cartService).removeItem(ITEM_ID);
        verifyNoMoreInteractions(cartService);
    }

    @Test
    void clearCart_ShouldReturnNoContent()
            throws Exception {

        mockMvc.perform(
                        delete("/api/cart")
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(cartService).clearCart();
        verifyNoMoreInteractions(cartService);
    }

    private CartResponse createCartResponse(
            int quantity
    ) {

        BigDecimal price =
                new BigDecimal("24.99");

        BigDecimal subtotal =
                price.multiply(
                        BigDecimal.valueOf(quantity)
                );

        CartItemResponse item =
                CartItemResponse.builder()
                        .id(ITEM_ID)
                        .productId(PRODUCT_ID)
                        .productName("Test Product")
                        .variantId(VARIANT_ID)
                        .quantity(quantity)
                        .price(price)
                        .subtotal(subtotal)
                        .build();

        return CartResponse.builder()
                .id(CART_ID)
                .userId(USER_ID)
                .items(List.of(item))
                .total(subtotal)
                .build();
    }
}