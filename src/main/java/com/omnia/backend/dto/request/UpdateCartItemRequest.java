package com.omnia.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCartItemRequest {

    @NotNull
    @Min(1)
    private Integer quantity;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CartItemResponse {

        private Long id;

        private Long productId;

        private String productName;

        private Long variantId;

        private Integer quantity;

        private BigDecimal price;

        private BigDecimal subtotal;
    }
}