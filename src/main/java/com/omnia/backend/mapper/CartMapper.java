package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.CartItemResponse;
import com.omnia.backend.dto.response.CartResponse;
import com.omnia.backend.entity.Cart;
import com.omnia.backend.entity.CartItem;

import java.math.BigDecimal;
import java.util.List;

public class CartMapper {

    public static CartItemResponse toItemResponse(CartItem item) {

        BigDecimal subtotal = item.getPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));

        return CartItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .variantId(item.getVariantId())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(subtotal)
                .build();
    }

    public static CartResponse toResponse(Cart cart, List<CartItem> items) {

        BigDecimal total = items.stream()
                .map(i -> i.getPrice()
                        .multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .id(cart.getId())
                .userId(cart.getUser().getId())
                .items(items.stream()
                        .map(CartMapper::toItemResponse)
                        .toList())
                .total(total)
                .build();
    }

    private CartMapper() {
    }
}