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

    /** Chosen size as shown to buyers ("M", "3XL", "42.5"); null for products without sizes. */
    private String sizeLabel;

    /** Stock left for this line: the size's stock, or the product's stock when unsized. */
    private Integer availableStock;

    private Integer quantity;

    private BigDecimal price;

    private BigDecimal subtotal;
}