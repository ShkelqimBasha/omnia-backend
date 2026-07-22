package com.omnia.backend.controller;

import com.omnia.backend.dto.response.FavoriteResponse;
import com.omnia.backend.service.interfaces.FavoriteService;
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
class FavoriteControllerTest {

    private static final Long FAVORITE_ID = 10L;
    private static final Long PRODUCT_ID = 20L;

    @Mock
    private FavoriteService favoriteService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        FavoriteController controller =
                new FavoriteController(favoriteService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void addFavorite_ShouldReturnCreatedFavorite()
            throws Exception {

        FavoriteResponse response =
                createResponse(
                        FAVORITE_ID,
                        PRODUCT_ID,
                        "Test Product"
                );

        when(
                favoriteService.addFavorite(PRODUCT_ID)
        ).thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/favorites/{productId}",
                                PRODUCT_ID
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
                                .value(FAVORITE_ID)
                )
                .andExpect(
                        jsonPath("$.productId")
                                .value(PRODUCT_ID)
                )
                .andExpect(
                        jsonPath("$.productName")
                                .value("Test Product")
                )
                .andExpect(
                        jsonPath("$.brand")
                                .value("Test Brand")
                )
                .andExpect(
                        jsonPath("$.price")
                                .value(99.99)
                )
                .andExpect(
                        jsonPath("$.discountPrice")
                                .value(79.99)
                )
                .andExpect(
                        jsonPath("$.category")
                                .value("Electronics")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE")
                );

        verify(favoriteService)
                .addFavorite(PRODUCT_ID);

        verifyNoMoreInteractions(favoriteService);
    }

    @Test
    void getFavorites_ShouldReturnFavoriteProducts()
            throws Exception {

        FavoriteResponse firstFavorite =
                createResponse(
                        10L,
                        20L,
                        "First Product"
                );

        FavoriteResponse secondFavorite =
                createResponse(
                        11L,
                        21L,
                        "Second Product"
                );

        secondFavorite.setBrand("Second Brand");
        secondFavorite.setPrice(
                new BigDecimal("49.99")
        );
        secondFavorite.setDiscountPrice(null);
        secondFavorite.setCategory("Books");

        when(favoriteService.getFavorites())
                .thenReturn(
                        List.of(
                                firstFavorite,
                                secondFavorite
                        )
                );

        mockMvc.perform(
                        get("/api/favorites")
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
                        jsonPath("$[0].productId")
                                .value(20L)
                )
                .andExpect(
                        jsonPath("$[0].productName")
                                .value("First Product")
                )
                .andExpect(
                        jsonPath("$[0].discountPrice")
                                .value(79.99)
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(11L)
                )
                .andExpect(
                        jsonPath("$[1].productId")
                                .value(21L)
                )
                .andExpect(
                        jsonPath("$[1].productName")
                                .value("Second Product")
                )
                .andExpect(
                        jsonPath("$[1].brand")
                                .value("Second Brand")
                )
                .andExpect(
                        jsonPath("$[1].price")
                                .value(49.99)
                )
                .andExpect(
                        jsonPath("$[1].discountPrice")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$[1].category")
                                .value("Books")
                );

        verify(favoriteService).getFavorites();
        verifyNoMoreInteractions(favoriteService);
    }

    @Test
    void removeFavorite_ShouldReturnNoContent()
            throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/favorites/{productId}",
                                PRODUCT_ID
                        )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(favoriteService)
                .removeFavorite(PRODUCT_ID);

        verifyNoMoreInteractions(favoriteService);
    }

    private FavoriteResponse createResponse(
            Long favoriteId,
            Long productId,
            String productName
    ) {

        return FavoriteResponse.builder()
                .id(favoriteId)
                .productId(productId)
                .productName(productName)
                .brand("Test Brand")
                .price(new BigDecimal("99.99"))
                .discountPrice(
                        new BigDecimal("79.99")
                )
                .category("Electronics")
                .status("ACTIVE")
                .build();
    }
}