package com.omnia.backend.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemResponse {

    private Long id;

    private Long productId;

    private String productName;

    private Long variantId;

    private Integer quantity;

    private BigDecimal price;

    private BigDecimal subtotal;
}