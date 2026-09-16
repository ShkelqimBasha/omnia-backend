package com.omnia.backend.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutResponse {

    private String checkoutReference;

    private Long userId;

    private Integer orderCount;

    private BigDecimal subtotalAmount;

    private BigDecimal shippingFee;

    private BigDecimal discountAmount;

    private BigDecimal totalAmount;

    private String couponCode;

    private LocalDateTime createdAt;

    private List<OrderResponse> orders;
}