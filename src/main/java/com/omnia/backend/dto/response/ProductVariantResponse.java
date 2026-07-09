package com.omnia.backend.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantResponse {

    private Long id;

    private Long productId;

    private String variantName;

    private String variantValue;

    private BigDecimal priceAdjustment;

    private Integer stock;
}