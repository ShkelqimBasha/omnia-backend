package com.omnia.backend.service.impl;

import com.omnia.backend.dto.request.AddToCartRequest;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import org.mockito.ArgumentCaptor;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.UpdateCartItemRequest;
import com.omnia.backend.dto.response.CartResponse;
import com.omnia.backend.entity.Cart;
import com.omnia.backend.entity.CartItem;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.CartItemRepository;
import com.omnia.backend.repository.CartRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    private User user;
    private Cart cart;
    private Product product;
    private CartItem cartItem;
    private AddToCartRequest addRequest;
    private UpdateCartItemRequest updateRequest;

    @BeforeEach
    void setUp() {

        user = User.builder()
                .id(1L)
                .username("shkelqim")
                .email("shkelqim@example.com")
                .build();

        cart = Cart.builder()
                .id(1L)
                .user(user)
                .build();

        product = Product.builder()
                .id(10L)
                .name("Samsung S24")
                .price(new BigDecimal("1200"))
                .discountPrice(new BigDecimal("1000"))
                .build();

        cartItem = CartItem.builder()
                .id(100L)
                .cart(cart)
                .product(product)
                .quantity(2)
                .price(new BigDecimal("1000"))
                .build();

        addRequest = AddToCartRequest.builder()
                .productId(10L)
                .variantId(null)
                .quantity(2)
                .build();

        updateRequest = UpdateCartItemRequest.builder()
                .quantity(5)
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "shkelqim@example.com",
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCart_shouldReturnExistingCart() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findByCartId(1L))
                .thenReturn(List.of(cartItem));

        CartResponse response =
                cartService.getCart();

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(1L, response.getUserId());
        assertEquals(1, response.getItems().size());
        assertEquals(
                new BigDecimal("2000"),
                response.getTotal()
        );

        verify(cartRepository).findByUserId(1L);
        verify(cartItemRepository).findByCartId(1L);
    }

    @Test
    void getCart_shouldCreateCartIfMissing() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.empty());

        when(cartRepository.save(any(Cart.class)))
                .thenReturn(cart);

        when(cartItemRepository.findByCartId(1L))
                .thenReturn(List.of());

        CartResponse response =
                cartService.getCart();

        assertNotNull(response);
        assertEquals(BigDecimal.ZERO, response.getTotal());

        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void addToCart_shouldAddNewItem() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(cartItemRepository.findByCartIdAndProductIdAndVariantId(
                1L,
                10L,
                null
        )).thenReturn(Optional.empty());

        when(cartItemRepository.save(any(CartItem.class)))
                .thenReturn(cartItem);

        when(cartItemRepository.findByCartId(1L))
                .thenReturn(List.of(cartItem));

        CartResponse response =
                cartService.addToCart(addRequest);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());

        verify(cartItemRepository)
                .save(any(CartItem.class));
    }

    @Test
    void addToCart_shouldIncreaseQuantityWhenItemExists() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(cartItemRepository.findByCartIdAndProductIdAndVariantId(
                1L,
                10L,
                null
        )).thenReturn(Optional.of(cartItem));

        when(cartItemRepository.save(any(CartItem.class)))
                .thenReturn(cartItem);

        when(cartItemRepository.findByCartId(1L))
                .thenReturn(List.of(cartItem));

        cartService.addToCart(addRequest);

        assertEquals(4, cartItem.getQuantity());

        verify(cartItemRepository).save(cartItem);
    }

    @Test
    void addToCart_shouldUseNormalPriceWhenDiscountMissing() {

        product.setDiscountPrice(null);

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(cartItemRepository.findByCartIdAndProductIdAndVariantId(
                anyLong(),
                anyLong(),
                any()
        )).thenReturn(Optional.empty());

        when(cartItemRepository.save(any(CartItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(cartItemRepository.findByCartId(1L))
                .thenReturn(List.of());

        cartService.addToCart(addRequest);

        verify(cartItemRepository).save(any(CartItem.class));
    }
    @Test
    void addToCart_shouldThrowWhenProductDoesNotExist() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(productRepository.findById(10L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> cartService.addToCart(addRequest)
                );

        assertEquals(
                "Product not found",
                exception.getMessage()
        );

        verify(cartItemRepository, never())
                .save(any(CartItem.class));
    }

    @Test
    void updateItem_shouldUpdateQuantitySuccessfully() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findById(100L))
                .thenReturn(Optional.of(cartItem));

        when(cartItemRepository.save(cartItem))
                .thenReturn(cartItem);

        when(cartItemRepository.findByCartId(1L))
                .thenReturn(List.of(cartItem));

        CartResponse response =
                cartService.updateItem(
                        100L,
                        updateRequest
                );

        assertNotNull(response);
        assertEquals(5, cartItem.getQuantity());
        assertEquals(1, response.getItems().size());
        assertEquals(
                new BigDecimal("5000"),
                response.getTotal()
        );

        verify(cartItemRepository).save(cartItem);
    }

    @Test
    void updateItem_shouldThrowWhenItemDoesNotExist() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findById(999L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> cartService.updateItem(
                                999L,
                                updateRequest
                        )
                );

        assertEquals(
                "Cart item not found",
                exception.getMessage()
        );

        verify(cartItemRepository, never())
                .save(any(CartItem.class));
    }

    @Test
    void updateItem_shouldThrowWhenItemBelongsToAnotherCart() {

        Cart anotherCart = Cart.builder()
                .id(2L)
                .user(user)
                .build();

        cartItem.setCart(anotherCart);

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findById(100L))
                .thenReturn(Optional.of(cartItem));

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> cartService.updateItem(
                                100L,
                                updateRequest
                        )
                );

        assertEquals(
                "You are not allowed to update this cart item",
                exception.getMessage()
        );

        verify(cartItemRepository, never())
                .save(any(CartItem.class));
    }

    @Test
    void removeItem_shouldRemoveItemSuccessfully() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findById(100L))
                .thenReturn(Optional.of(cartItem));

        cartService.removeItem(100L);

        verify(cartItemRepository).delete(cartItem);
    }

    @Test
    void removeItem_shouldThrowWhenItemDoesNotExist() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findById(999L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> cartService.removeItem(999L)
                );

        assertEquals(
                "Cart item not found",
                exception.getMessage()
        );

        verify(cartItemRepository, never())
                .delete(any(CartItem.class));
    }

    @Test
    void removeItem_shouldThrowWhenItemBelongsToAnotherCart() {

        Cart anotherCart = Cart.builder()
                .id(2L)
                .user(user)
                .build();

        cartItem.setCart(anotherCart);

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findById(100L))
                .thenReturn(Optional.of(cartItem));

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> cartService.removeItem(100L)
                );

        assertEquals(
                "You are not allowed to remove this cart item",
                exception.getMessage()
        );

        verify(cartItemRepository, never())
                .delete(any(CartItem.class));
    }

    @Test
    void clearCart_shouldDeleteAllCartItems() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        cartService.clearCart();

        verify(cartItemRepository)
                .deleteByCartId(1L);
    }
    @Test
    void getCurrentUser_shouldFallbackToUsername() {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "shkelqim",
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        when(userRepository.findByEmail("shkelqim"))
                .thenReturn(Optional.empty());

        when(userRepository.findByUsername("shkelqim"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findByCartId(1L))
                .thenReturn(List.of(cartItem));

        CartResponse response =
                cartService.getCart();

        assertNotNull(response);
        assertEquals(1L, response.getUserId());

        verify(userRepository)
                .findByUsername("shkelqim");
    }

    @Test
    void getCurrentUser_shouldThrowWhenUserDoesNotExist() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.empty());

        when(userRepository.findByUsername("shkelqim@example.com"))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> cartService.getCart()
                );

        assertEquals(
                "User not found",
                exception.getMessage()
        );

        verifyNoInteractions(
                cartRepository,
                cartItemRepository,
                productRepository
        );
    }

    @Test
    void addToCart_shouldCreateCartWhenUserHasNoCart() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.empty());

        when(cartRepository.save(any(Cart.class)))
                .thenAnswer(invocation -> {
                    Cart createdCart = invocation.getArgument(0);
                    createdCart.setId(1L);
                    return createdCart;
                });

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(cartItemRepository.findByCartIdAndProductIdAndVariantId(
                1L,
                10L,
                null
        )).thenReturn(Optional.empty());

        when(cartItemRepository.save(any(CartItem.class)))
                .thenAnswer(invocation -> {
                    CartItem createdItem = invocation.getArgument(0);
                    createdItem.setId(100L);
                    return createdItem;
                });

        when(cartItemRepository.findByCartId(1L))
                .thenAnswer(invocation -> List.of(
                        CartItem.builder()
                                .id(100L)
                                .cart(cart)
                                .product(product)
                                .quantity(2)
                                .price(new BigDecimal("1000"))
                                .build()
                ));

        CartResponse response =
                cartService.addToCart(addRequest);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals(
                new BigDecimal("2000"),
                response.getTotal()
        );

        verify(cartRepository)
                .save(any(Cart.class));
    }

    @Test
    void addToCart_shouldUseDiscountPrice() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(cartItemRepository.findByCartIdAndProductIdAndVariantId(
                1L,
                10L,
                null
        )).thenReturn(Optional.empty());

        when(cartItemRepository.save(any(CartItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(cartItemRepository.findByCartId(1L))
                .thenReturn(List.of());

        cartService.addToCart(addRequest);

        ArgumentCaptor<CartItem> itemCaptor =
                ArgumentCaptor.forClass(CartItem.class);

        verify(cartItemRepository)
                .save(itemCaptor.capture());

        CartItem savedItem = itemCaptor.getValue();

        assertEquals(
                new BigDecimal("1000"),
                savedItem.getPrice()
        );
    }

    @Test
    void addToCart_shouldUseNormalPriceWhenDiscountPriceIsNull() {

        product.setDiscountPrice(null);

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(cartItemRepository.findByCartIdAndProductIdAndVariantId(
                1L,
                10L,
                null
        )).thenReturn(Optional.empty());

        when(cartItemRepository.save(any(CartItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(cartItemRepository.findByCartId(1L))
                .thenReturn(List.of());

        cartService.addToCart(addRequest);

        ArgumentCaptor<CartItem> itemCaptor =
                ArgumentCaptor.forClass(CartItem.class);

        verify(cartItemRepository)
                .save(itemCaptor.capture());

        CartItem savedItem = itemCaptor.getValue();

        assertEquals(
                new BigDecimal("1200"),
                savedItem.getPrice()
        );
    }

    @Test
    void addToCart_shouldPreserveVariantId() {

        addRequest.setVariantId(55L);

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(cartRepository.findByUserId(1L))
                .thenReturn(Optional.of(cart));

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(cartItemRepository.findByCartIdAndProductIdAndVariantId(
                1L,
                10L,
                55L
        )).thenReturn(Optional.empty());

        when(cartItemRepository.save(any(CartItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(cartItemRepository.findByCartId(1L))
                .thenReturn(List.of());

        cartService.addToCart(addRequest);

        ArgumentCaptor<CartItem> itemCaptor =
                ArgumentCaptor.forClass(CartItem.class);

        verify(cartItemRepository)
                .save(itemCaptor.capture());

        assertEquals(
                55L,
                itemCaptor.getValue().getVariantId()
        );
    }
}