package com.omnia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnia.backend.dto.request.ProductVariantRequest;
import com.omnia.backend.dto.response.ProductVariantResponse;
import com.omnia.backend.service.interfaces.ProductVariantService;
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
import static org.mockito.Mockito.verify;
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
class ProductVariantControllerTest {

    private static final Long PRODUCT_ID = 10L;
    private static final Long VARIANT_ID = 20L;

    @Mock
    private ProductVariantService productVariantService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        ProductVariantController controller =
                new ProductVariantController(
                        productVariantService
                );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        objectMapper = new ObjectMapper()
                .findAndRegisterModules();
    }

    @Test
    void addVariant_WithValidRequest_ShouldReturnCreated()
            throws Exception {

        ProductVariantRequest request =
                createRequest(
                        "Color",
                        "Black",
                        "10.50",
                        25
                );

        ProductVariantResponse response =
                createResponse(
                        VARIANT_ID,
                        "Color",
                        "Black",
                        "10.50",
                        25
                );

        when(
                productVariantService.addVariant(
                        eq(PRODUCT_ID),
                        any(ProductVariantRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/product-variants/product/{productId}",
                                PRODUCT_ID
                        )
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
                                .value(VARIANT_ID)
                )
                .andExpect(
                        jsonPath("$.productId")
                                .value(PRODUCT_ID)
                )
                .andExpect(
                        jsonPath("$.variantName")
                                .value("Color")
                )
                .andExpect(
                        jsonPath("$.variantValue")
                                .value("Black")
                )
                .andExpect(
                        jsonPath("$.priceAdjustment")
                                .value(10.50)
                )
                .andExpect(
                        jsonPath("$.stock")
                                .value(25)
                );

        ArgumentCaptor<ProductVariantRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        ProductVariantRequest.class
                );

        verify(productVariantService).addVariant(
                eq(PRODUCT_ID),
                requestCaptor.capture()
        );

        ProductVariantRequest capturedRequest =
                requestCaptor.getValue();

        assertThat(capturedRequest.getVariantName())
                .isEqualTo("Color");

        assertThat(capturedRequest.getVariantValue())
                .isEqualTo("Black");

        assertThat(capturedRequest.getPriceAdjustment())
                .isEqualByComparingTo("10.50");

        assertThat(capturedRequest.getStock())
                .isEqualTo(25);

        verifyNoMoreInteractions(productVariantService);
    }

    @Test
    void getVariants_ShouldReturnProductVariants()
            throws Exception {

        ProductVariantResponse firstVariant =
                createResponse(
                        20L,
                        "Color",
                        "Black",
                        "10.50",
                        25
                );

        ProductVariantResponse secondVariant =
                createResponse(
                        21L,
                        "Size",
                        "Large",
                        "5.00",
                        15
                );

        when(
                productVariantService
                        .getProductVariants(PRODUCT_ID)
        ).thenReturn(
                List.of(
                        firstVariant,
                        secondVariant
                )
        );

        mockMvc.perform(
                        get(
                                "/api/product-variants/product/{productId}",
                                PRODUCT_ID
                        )
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
                                .value(20L)
                )
                .andExpect(
                        jsonPath("$[0].productId")
                                .value(PRODUCT_ID)
                )
                .andExpect(
                        jsonPath("$[0].variantName")
                                .value("Color")
                )
                .andExpect(
                        jsonPath("$[0].variantValue")
                                .value("Black")
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(21L)
                )
                .andExpect(
                        jsonPath("$[1].variantName")
                                .value("Size")
                )
                .andExpect(
                        jsonPath("$[1].variantValue")
                                .value("Large")
                )
                .andExpect(
                        jsonPath("$[1].stock")
                                .value(15)
                );

        verify(productVariantService)
                .getProductVariants(PRODUCT_ID);

        verifyNoMoreInteractions(productVariantService);
    }

    @Test
    void updateVariant_WithValidRequest_ShouldReturnUpdatedVariant()
            throws Exception {

        ProductVariantRequest request =
                createRequest(
                        "Color",
                        "White",
                        "12.75",
                        30
                );

        ProductVariantResponse response =
                createResponse(
                        VARIANT_ID,
                        "Color",
                        "White",
                        "12.75",
                        30
                );

        when(
                productVariantService.updateVariant(
                        eq(VARIANT_ID),
                        any(ProductVariantRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        put(
                                "/api/product-variants/{variantId}",
                                VARIANT_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
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
                .andExpect(
                        jsonPath("$.id")
                                .value(VARIANT_ID)
                )
                .andExpect(
                        jsonPath("$.productId")
                                .value(PRODUCT_ID)
                )
                .andExpect(
                        jsonPath("$.variantName")
                                .value("Color")
                )
                .andExpect(
                        jsonPath("$.variantValue")
                                .value("White")
                )
                .andExpect(
                        jsonPath("$.priceAdjustment")
                                .value(12.75)
                )
                .andExpect(
                        jsonPath("$.stock")
                                .value(30)
                );

        ArgumentCaptor<ProductVariantRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        ProductVariantRequest.class
                );

        verify(productVariantService).updateVariant(
                eq(VARIANT_ID),
                requestCaptor.capture()
        );

        ProductVariantRequest capturedRequest =
                requestCaptor.getValue();

        assertThat(capturedRequest.getVariantName())
                .isEqualTo("Color");

        assertThat(capturedRequest.getVariantValue())
                .isEqualTo("White");

        assertThat(capturedRequest.getPriceAdjustment())
                .isEqualByComparingTo("12.75");

        assertThat(capturedRequest.getStock())
                .isEqualTo(30);

        verifyNoMoreInteractions(productVariantService);
    }

    @Test
    void deleteVariant_ShouldReturnNoContent()
            throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/product-variants/{variantId}",
                                VARIANT_ID
                        )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(productVariantService)
                .deleteVariant(VARIANT_ID);

        verifyNoMoreInteractions(productVariantService);
    }

    private ProductVariantRequest createRequest(
            String variantName,
            String variantValue,
            String priceAdjustment,
            Integer stock
    ) {

        return ProductVariantRequest.builder()
                .variantName(variantName)
                .variantValue(variantValue)
                .priceAdjustment(
                        new BigDecimal(priceAdjustment)
                )
                .stock(stock)
                .build();
    }

    private ProductVariantResponse createResponse(
            Long id,
            String variantName,
            String variantValue,
            String priceAdjustment,
            Integer stock
    ) {

        return ProductVariantResponse.builder()
                .id(id)
                .productId(PRODUCT_ID)
                .variantName(variantName)
                .variantValue(variantValue)
                .priceAdjustment(
                        new BigDecimal(priceAdjustment)
                )
                .stock(stock)
                .build();
    }
}