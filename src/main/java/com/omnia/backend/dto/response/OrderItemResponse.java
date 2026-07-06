package com.omnia.backend.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse {

    private Long productId;

    private String productName;

    private String productImage;

    private String variantInfo;

    private BigDecimal unitPrice;

    private Integer quantity;

    private BigDecimal subtotal;
}