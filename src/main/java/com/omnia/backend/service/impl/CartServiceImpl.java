package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.AddToCartRequest;
import com.omnia.backend.dto.request.UpdateCartItemRequest;
import com.omnia.backend.dto.response.CartResponse;
import com.omnia.backend.entity.Cart;
import com.omnia.backend.entity.CartItem;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.User;
import com.omnia.backend.mapper.CartMapper;
import com.omnia.backend.repository.CartItemRepository;
import com.omnia.backend.repository.CartRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.interfaces.CartService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartServiceImpl(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart() {
        User user = getCurrentUser();
        Cart cart = getOrCreateCart(user);
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());

        return CartMapper.toResponse(cart, items);
    }

    @Override
    @Transactional
    public CartResponse addToCart(AddToCartRequest request) {
        User user = getCurrentUser();
        Cart cart = getOrCreateCart(user);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        BigDecimal price = product.getDiscountPrice() != null
                ? product.getDiscountPrice()
                : product.getPrice();

        CartItem cartItem = cartItemRepository
                .findByCartIdAndProductIdAndVariantId(
                        cart.getId(),
                        product.getId(),
                        request.getVariantId()
                )
                .orElse(null);

        if (cartItem == null) {
            cartItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .variantId(request.getVariantId())
                    .quantity(request.getQuantity())
                    .price(price)
                    .build();
        } else {
            cartItem.setQuantity(cartItem.getQuantity() + request.getQuantity());
            cartItem.setPrice(price);
        }

        cartItemRepository.save(cartItem);

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        return CartMapper.toResponse(cart, items);
    }

    @Override
    @Transactional
    public CartResponse updateItem(Long itemId, UpdateCartItemRequest request) {
        User user = getCurrentUser();
        Cart cart = getOrCreateCart(user);

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new RuntimeException("You are not allowed to update this cart item");
        }

        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        return CartMapper.toResponse(cart, items);
    }

    @Override
    @Transactional
    public void removeItem(Long itemId) {
        User user = getCurrentUser();
        Cart cart = getOrCreateCart(user);

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new RuntimeException("You are not allowed to remove this cart item");
        }

        cartItemRepository.delete(item);
    }

    @Override
    @Transactional
    public void clearCart() {
        User user = getCurrentUser();
        Cart cart = getOrCreateCart(user);

        cartItemRepository.deleteByCartId(cart.getId());
    }

    private Cart getOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(
                        Cart.builder()
                                .user(user)
                                .build()
                ));
    }

    private User getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String usernameOrEmail = authentication.getName();

        return userRepository.findByEmail(usernameOrEmail)
                .or(() -> userRepository.findByUsername(usernameOrEmail))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}