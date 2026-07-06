package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.OrderItemResponse;
import com.omnia.backend.dto.response.OrderResponse;
import com.omnia.backend.entity.Order;
import com.omnia.backend.entity.OrderItem;

import java.util.List;

public class OrderMapper {

    public static OrderItemResponse toItemResponse(OrderItem item) {

        return OrderItemResponse.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .productImage(item.getProductImage())
                .variantInfo(item.getVariantInfo())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .build();
    }

    public static OrderResponse toResponse(Order order, List<OrderItem> items) {

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .addressId(order.getAddressId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .items(items.stream()
                        .map(OrderMapper::toItemResponse)
                        .toList())
                .build();
    }

    private OrderMapper() {
    }
}