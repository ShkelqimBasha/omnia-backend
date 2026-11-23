package com.omnia.backend.service.impl;

import com.omnia.backend.dto.request.CreateOrderItemRequest;
import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.dto.response.CheckoutResponse;
import com.omnia.backend.dto.response.OrderItemResponse;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Checkout takes stock per size and writes the size on the order line. */
@ExtendWith(MockitoExtension.class)
class CheckoutSizesTest {

    @Mock private CheckoutGroupRepository checkoutGroupRepository;
    @Mock private CheckoutGroupCouponRepository checkoutGroupCouponRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderCouponRepository orderCouponRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CheckoutServiceImpl checkoutService;
    private Product shirt;
    private Product mug;
    private ProductVariant m;
    private ProductVariant l;

    @BeforeEach
    void setUp() {
        checkoutService = new CheckoutServiceImpl(checkoutGroupRepository, checkoutGroupCouponRepository,
                orderRepository, orderCouponRepository, orderItemRepository, orderStatusHistoryRepository,
                productRepository, userRepository, couponRepository, paymentRepository, eventPublisher);
        User buyer = User.builder().id(1L).username("ana").email("ana@example.com").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ana@example.com", null, AuthorityUtils.NO_AUTHORITIES));
        lenient().when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(buyer));

        Organization shop = Organization.builder().id(5L).name("Dyqani").slug("dyqani")
                .status(OrganizationStatus.ACTIVE).build();
        shirt = Product.builder().id(10L).name("Bluzë").organization(shop).price(new BigDecimal("20.00"))
                .category(Category.builder().id(1L).name("Veshje").kind(CategoryKind.CLOTHING).build())
                .stock(5).build();
        m = ProductVariant.builder().id(101L).product(shirt).variantName("SIZE").variantValue("M").stock(3).build();
        l = ProductVariant.builder().id(102L).product(shirt).variantName("SIZE").variantValue("XXXL").stock(2).build();
        shirt.getVariants().addAll(List.of(m, l));
        mug = Product.builder().id(20L).name("Filxhan").organization(shop).price(new BigDecimal("5.00"))
                .category(Category.builder().id(2L).name("Shtëpi").kind(CategoryKind.GENERAL).build())
                .stock(4).build();

        lenient().when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(shirt));
        lenient().when(productRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(mug));
        lenient().when(checkoutGroupRepository.save(any())).thenAnswer(invocation -> {
            CheckoutGroup group = invocation.getArgument(0);
            group.setId(100L);
            group.setCheckoutReference("ref-1");
            return group;
        });
        AtomicLong orderIds = new AtomicLong(200L);
        lenient().when(orderRepository.save(any())).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(orderIds.getAndIncrement());
            return order;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static CreateOrderItemRequest item(long productId, Long variantId, int quantity) {
        return CreateOrderItemRequest.builder().productId(productId).variantId(variantId).quantity(quantity).build();
    }

    private static CreateOrderRequest request(CreateOrderItemRequest... items) {
        return CreateOrderRequest.builder().addressId(1L).shippingName("Ana").shippingEmail("ana@example.com")
                .shippingPhone("+355690000000").shippingAddress("Tiranë").items(List.of(items)).build();
    }

    @Test
    void twoSizesOfOneProductAreTwoLinesAndTakeStockPerSize() {
        CheckoutResponse response = checkoutService.checkout(request(
                item(10L, 102L, 1), item(10L, 101L, 2), item(10L, 101L, 1), item(20L, null, 2)));

        assertEquals(0, m.getStock());
        assertEquals(1, l.getStock());
        assertEquals(1, shirt.getStock(), "product stock is the sum of its sizes");
        assertEquals(2, mug.getStock(), "unsized products work as before");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderItem>> lines = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(lines.capture());
        List<OrderItem> saved = lines.getValue();
        assertEquals(3, saved.size());
        assertEquals(101L, saved.get(0).getVariantId());
        assertEquals("Masa: M", saved.get(0).getVariantInfo());
        assertEquals(3, saved.get(0).getQuantity());
        assertEquals(102L, saved.get(1).getVariantId());
        assertEquals("Masa: 3XL", saved.get(1).getVariantInfo());
        assertNull(saved.get(2).getVariantId());
        assertNull(saved.get(2).getVariantInfo());

        List<OrderItemResponse> shown = response.getOrders().get(0).getItems();
        assertEquals("Masa: M", shown.get(0).getVariantInfo());
        assertEquals(101L, shown.get(0).getVariantId());
        assertEquals(new BigDecimal("90.00"), response.getSubtotalAmount());
    }

    @Test
    void missingSizeIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout(request(item(10L, null, 1))));
        assertEquals(3, m.getStock());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void sizeOfAnotherProductIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout(request(item(10L, 999L, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout(request(item(20L, 101L, 1))));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void notEnoughStockOfTheSizeIsRefusedEvenWhenTheProductHasMore() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout(request(item(10L, 102L, 3))));
        assertTrue(e.getMessage().contains("3XL"), e.getMessage());
        assertEquals(2, l.getStock());
        assertEquals(5, shirt.getStock());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void linesOfTheSameSizeAreAddedUpBeforeTheCheck() {
        assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout(request(item(10L, 101L, 2), item(10L, 101L, 2))));
    }

    @Test
    void unsizedCheckoutIsUnchanged() {
        checkoutService.checkout(request(item(20L, null, 4)));
        assertEquals(0, mug.getStock());
        assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout(request(item(20L, null, 1))));
    }

    @Test
    void unsizedRequestJsonHasNoVariantField() throws Exception {
        // PayPal checkout keys hash the request JSON; unsized requests must hash as before.
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(item(20L, null, 1));
        assertFalse(json.contains("variantId"), json);
    }
}
