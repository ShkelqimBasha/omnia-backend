package com.omnia.backend.service.impl;

import com.omnia.backend.dto.request.AddToCartRequest;
import com.omnia.backend.dto.request.UpdateCartItemRequest;
import com.omnia.backend.dto.response.CartItemResponse;
import com.omnia.backend.dto.response.CartResponse;
import com.omnia.backend.entity.Cart;
import com.omnia.backend.entity.CartItem;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductVariant;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CategoryKind;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cart rules for clothing and shoe sizes. */
@ExtendWith(MockitoExtension.class)
class CartSizesTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private CartServiceImpl cartService;

    private Cart cart;
    private Product shoe;
    private ProductVariant size42;
    private ProductVariant size43;
    private final List<CartItem> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        User user = User.builder().id(1L).username("ana").email("ana@example.com").build();
        cart = Cart.builder().id(1L).user(user).build();
        shoe = Product.builder().id(10L).name("Atlete").price(new BigDecimal("60.00"))
                .category(Category.builder().id(2L).name("Atlete & Këpucë").kind(CategoryKind.SHOES).build())
                .stock(4).build();
        size42 = ProductVariant.builder().id(42L).product(shoe).variantName("SIZE").variantValue("42.5").stock(3).build();
        size43 = ProductVariant.builder().id(43L).product(shoe).variantName("SIZE").variantValue("43").stock(1).build();
        shoe.getVariants().addAll(List.of(size42, size43));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ana@example.com", null, AuthorityUtils.NO_AUTHORITIES));
        lenient().when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
        lenient().when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        lenient().when(productRepository.findById(10L)).thenReturn(Optional.of(shoe));
        lenient().when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> {
            CartItem item = invocation.getArgument(0);
            if (!saved.contains(item)) saved.add(item);
            return item;
        });
        lenient().when(cartItemRepository.findByCartId(1L)).thenReturn(saved);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private AddToCartRequest add(Long variantId, int quantity) {
        return AddToCartRequest.builder().productId(10L).variantId(variantId).quantity(quantity).build();
    }

    @Test
    void sizeIsRequiredForASizedProduct() {
        assertThrows(IllegalArgumentException.class, () -> cartService.addToCart(add(null, 1)));
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void sizeMustBelongToTheProduct() {
        assertThrows(IllegalArgumentException.class, () -> cartService.addToCart(add(999L, 1)));
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void quantityCannotExceedTheSizeStock() {
        assertThrows(IllegalArgumentException.class, () -> cartService.addToCart(add(42L, 4)));
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addingAgainCountsWhatIsAlreadyInTheCart() {
        CartItem existing = CartItem.builder().id(5L).cart(cart).product(shoe).variantId(42L)
                .variant(size42).quantity(2).price(new BigDecimal("60.00")).build();
        when(cartItemRepository.findByCartIdAndProductIdAndVariantId(1L, 10L, 42L))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> cartService.addToCart(add(42L, 2)));
        assertEquals(2, existing.getQuantity());

        cartService.addToCart(add(42L, 1));
        assertEquals(3, existing.getQuantity());
    }

    @Test
    void eachSizeIsItsOwnLineWithLabelAndStock() {
        cartService.addToCart(add(42L, 2));
        CartResponse response = cartService.addToCart(add(43L, 1));

        assertEquals(2, response.getItems().size());
        CartItemResponse first = response.getItems().get(0);
        assertEquals(42L, first.getVariantId());
        assertEquals("42.5", first.getSizeLabel());
        assertEquals(3, first.getAvailableStock());
        CartItemResponse second = response.getItems().get(1);
        assertEquals("43", second.getSizeLabel());
        assertEquals(1, second.getAvailableStock());
    }

    @Test
    void soldOutSizeCannotBeAdded() {
        size43.setStock(0);
        assertThrows(IllegalArgumentException.class, () -> cartService.addToCart(add(43L, 1)));
    }

    @Test
    void updatingALineChecksTheSizeStock() {
        CartItem line = CartItem.builder().id(5L).cart(cart).product(shoe).variantId(42L)
                .variant(size42).quantity(1).price(new BigDecimal("60.00")).build();
        when(cartItemRepository.findById(5L)).thenReturn(Optional.of(line));

        assertThrows(IllegalArgumentException.class,
                () -> cartService.updateItem(5L, UpdateCartItemRequest.builder().quantity(4).build()));
        assertEquals(1, line.getQuantity());

        cartService.updateItem(5L, UpdateCartItemRequest.builder().quantity(3).build());
        assertEquals(3, line.getQuantity());
    }

    @Test
    void productsWithoutSizesRefuseASizeAndWorkAsBefore() {
        Product phone = Product.builder().id(11L).name("Telefon").price(new BigDecimal("100"))
                .category(Category.builder().id(3L).name("Elektronike").kind(CategoryKind.GENERAL).build())
                .stock(1).build();
        when(productRepository.findById(11L)).thenReturn(Optional.of(phone));

        assertThrows(IllegalArgumentException.class, () -> cartService.addToCart(
                AddToCartRequest.builder().productId(11L).variantId(42L).quantity(1).build()));

        // As before sizes: no stock check in the cart for unsized products (checkout checks).
        CartResponse response = cartService.addToCart(
                AddToCartRequest.builder().productId(11L).quantity(2).build());
        CartItemResponse line = response.getItems().get(0);
        assertNull(line.getVariantId());
        assertNull(line.getSizeLabel());
        assertEquals(1, line.getAvailableStock());
        assertEquals(2, line.getQuantity());
    }
}
