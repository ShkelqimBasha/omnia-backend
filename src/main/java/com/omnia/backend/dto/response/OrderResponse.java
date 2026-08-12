package com.omnia.backend.dto.response;

import com.omnia.backend.enums.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long id;

    private Long userId;

    private Long addressId;

    private String shippingName;

    private String shippingEmail;

    private String shippingPhone;

    private String shippingAddress;

    private BigDecimal totalAmount;

    private BigDecimal subtotalAmount;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    private String couponCode;

    private OrderStatus status;

    private LocalDateTime createdAt;

    private List<OrderItemResponse> items;
}