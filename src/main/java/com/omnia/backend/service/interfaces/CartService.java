package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.AddToCartRequest;
import com.omnia.backend.dto.request.UpdateCartItemRequest;
import com.omnia.backend.dto.response.CartResponse;

public interface CartService {

    CartResponse getCart();

    CartResponse addToCart(AddToCartRequest request);

    CartResponse updateItem(Long itemId, UpdateCartItemRequest request);

    void removeItem(Long itemId);

    void clearCart();
}