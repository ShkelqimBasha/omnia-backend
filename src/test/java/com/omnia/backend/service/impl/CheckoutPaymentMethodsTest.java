package com.omnia.backend.service.impl;

import com.omnia.backend.dto.request.CreateOrderItemRequest;
import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

/** Checkout only allows the payment methods every company in it accepts. */
@ExtendWith(MockitoExtension.class)
class CheckoutPaymentMethodsTest {

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
    private Organization shop;
    private Organization bakery;

    @BeforeEach
    void setUp() {
        checkoutService = new CheckoutServiceImpl(checkoutGroupRepository, checkoutGroupCouponRepository,
                orderRepository, orderCouponRepository, orderItemRepository, orderStatusHistoryRepository,
                productRepository, userRepository, couponRepository, paymentRepository, eventPublisher);
        User buyer = User.builder().id(1L).username("ana").email("ana@example.com").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ana@example.com", null, AuthorityUtils.NO_AUTHORITIES));
        lenient().when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(buyer));

        shop = Organization.builder().id(5L).name("Dyqani").slug("dyqani").status(OrganizationStatus.ACTIVE).build();
        bakery = Organization.builder().id(6L).name("Furra").slug("furra").status(OrganizationStatus.ACTIVE).build();
        Category general = Category.builder().id(2L).name("Shtëpi").kind(CategoryKind.GENERAL).build();
        Product mug = Product.builder().id(20L).name("Filxhan").organization(shop)
                .price(new BigDecimal("15.00")).category(general).stock(50).build();
        Product bread = Product.builder().id(30L).name("Bukë").organization(bakery)
                .price(new BigDecimal("2.00")).discountPrice(new BigDecimal("1.50")).category(general).stock(50).build();
        lenient().when(productRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(mug));
        lenient().when(productRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(bread));
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

    private static CreateOrderRequest request(long... productIdAndQuantity) {
        List<CreateOrderItemRequest> items = new java.util.ArrayList<>();
        for (int i = 0; i < productIdAndQuantity.length; i += 2) {
            items.add(CreateOrderItemRequest.builder().productId(productIdAndQuantity[i])
                    .quantity((int) productIdAndQuantity[i + 1]).build());
        }
        return CreateOrderRequest.builder().addressId(1L).shippingName("Ana").shippingEmail("ana@example.com")
                .shippingPhone("+355690000000").shippingAddress("Tiranë").items(items).build();
    }

    @Test
    void defaultsAcceptBothMethodsAsBefore() {
        assertTrue(shop.isAcceptsCashOnDelivery());
        assertTrue(shop.isAcceptsPaypal());
        assertTrue(new Organization().isAcceptsCashOnDelivery(), "no-args constructor keeps the defaults");
        assertTrue(new Organization().isAcceptsPaypal());
        assertEquals(2, checkoutService.checkout(request(20, 1, 30, 2)).getOrderCount());
        assertEquals(2, checkoutService.checkout(request(20, 1, 30, 2), PaymentMethod.PAYPAL).getOrderCount());
    }

    @Test
    void oneCompanyWithoutCodBlocksCodForTheWholeCartButNotPaypal() {
        bakery.setAcceptsCashOnDelivery(false);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout(request(20, 1, 30, 2)));
        assertEquals("Biznesi «Furra» nuk pranon pagesë në dorëzim.", refused.getMessage());
        verify(checkoutGroupRepository, never()).save(any());
        assertEquals(2, checkoutService.checkout(request(20, 1, 30, 2), PaymentMethod.PAYPAL).getOrderCount());
        // The shop alone still takes COD.
        assertEquals(1, checkoutService.checkout(request(20, 1)).getOrderCount());
    }

    @Test
    void companyWithoutPaypalBlocksPaypal() {
        shop.setAcceptsPaypal(false);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout(request(20, 1, 30, 2), PaymentMethod.PAYPAL));
        assertEquals("Biznesi «Dyqani» nuk pranon pagesë me PayPal.", refused.getMessage());
        assertEquals(2, checkoutService.checkout(request(20, 1, 30, 2)).getOrderCount());
    }

    @Test
    void codLimitsUseTheCompanyProductValueWithDiscountPrice() {
        bakery.setCodMinOrderAmount(new BigDecimal("3.00"));
        bakery.setCodMaxOrderAmount(new BigDecimal("6.00"));
        // 1 × 1.50 = 1.50 < 3.00
        IllegalArgumentException below = assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout(request(30, 1)));
        assertEquals("Për pagesë në dorëzim, produktet nga «Furra» duhet të jenë të paktën 3,00 €.",
                below.getMessage());
        // 2 × 1.50 = 3.00 (limit included); the shop's 15.00 does not count for the bakery.
        assertEquals(2, checkoutService.checkout(request(20, 1, 30, 2)).getOrderCount());
        assertEquals(1, checkoutService.checkout(request(30, 4)).getOrderCount(), "6.00 is still allowed");
        IllegalArgumentException above = assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout(request(30, 5)));
        assertEquals("Pagesa në dorëzim për produktet nga «Furra» pranohet deri në 6,00 €.", above.getMessage());
        // COD limits do not touch PayPal.
        assertEquals(1, checkoutService.checkout(request(30, 5), PaymentMethod.PAYPAL).getOrderCount());
    }

    @Test
    void otherMethodsAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout(request(20, 1), PaymentMethod.CARD));
    }

    @Test
    void rulesExplainMixedCarts() {
        bakery.setAcceptsCashOnDelivery(false);
        CheckoutPaymentRules.Result mixed = CheckoutPaymentRules.evaluate(List.of(
                new CheckoutPaymentRules.Part(shop, new BigDecimal("15")),
                new CheckoutPaymentRules.Part(bakery, new BigDecimal("3"))));
        assertFalse(mixed.available(PaymentMethod.CASH_ON_DELIVERY));
        assertTrue(mixed.available(PaymentMethod.PAYPAL));
        assertTrue(mixed.mixedRules());
        assertTrue(mixed.message().startsWith("Shporta ka produkte nga disa biznese"));

        shop.setAcceptsPaypal(false);
        CheckoutPaymentRules.Result none = CheckoutPaymentRules.evaluate(List.of(
                new CheckoutPaymentRules.Part(shop, new BigDecimal("15")),
                new CheckoutPaymentRules.Part(bakery, new BigDecimal("3"))));
        assertFalse(none.available(PaymentMethod.PAYPAL));
        assertTrue(none.message().contains("Bëj porosi të veçanta"));

        CheckoutPaymentRules.Result single = CheckoutPaymentRules.evaluate(List.of(
                new CheckoutPaymentRules.Part(bakery, new BigDecimal("3"))));
        assertFalse(single.mixedRules(), "one company is never a mixed cart");
        assertNull(single.message());
        assertEquals("12,50 €", CheckoutPaymentRules.euros(new BigDecimal("12.5")));
    }
}
