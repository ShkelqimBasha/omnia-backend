package com.omnia.backend.dto.request;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantRequest {

    private String variantName;

    private String variantValue;

    private BigDecimal priceAdjustment;

    private Integer stock;
}