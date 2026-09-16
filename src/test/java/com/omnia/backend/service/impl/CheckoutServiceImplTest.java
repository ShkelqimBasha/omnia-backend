package com.omnia.backend.service.impl;

import com.omnia.backend.dto.request.CreateOrderItemRequest;
import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.dto.response.CheckoutResponse;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.CouponStatus;
import com.omnia.backend.enums.DiscountType;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.event.OrderStatusChangedEvent;
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

@ExtendWith(MockitoExtension.class)
class CheckoutServiceImplTest {

    @Mock
    private CheckoutGroupRepository checkoutGroupRepository;

    @Mock
    private CheckoutGroupCouponRepository
            checkoutGroupCouponRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderCouponRepository orderCouponRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderStatusHistoryRepository
            orderStatusHistoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CheckoutServiceImpl checkoutService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        checkoutService = new CheckoutServiceImpl(
                checkoutGroupRepository,
                checkoutGroupCouponRepository,
                orderRepository,
                orderCouponRepository,
                orderItemRepository,
                orderStatusHistoryRepository,
                productRepository,
                userRepository,
                couponRepository,
                paymentRepository,
                eventPublisher
        );

        currentUser = User.builder()
                .id(1L)
                .username("shkelqim")
                .email("shkelqim@example.com")
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

        when(
                userRepository.findByEmail(
                        "shkelqim@example.com"
                )
        ).thenReturn(Optional.of(currentUser));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void checkout_WithTwoOrganizations_ShouldCreateGroupedOrders() {
        Organization firstOrganization =
                Organization.builder()
                        .id(1L)
                        .name("Omnia Store")
                        .slug("omnia-store")
                        .status(
                                OrganizationStatus.ACTIVE
                        )
                        .build();

        Organization secondOrganization =
                Organization.builder()
                        .id(2L)
                        .name("Cimi Test")
                        .slug("cimi-test")
                        .status(
                                OrganizationStatus.ACTIVE
                        )
                        .build();

        Product firstProduct = Product.builder()
                .id(10L)
                .name("Telefon")
                .organization(firstOrganization)
                .price(new BigDecimal("40.00"))
                .stock(10)
                .build();

        Product secondProduct = Product.builder()
                .id(20L)
                .name("Rimel")
                .organization(secondOrganization)
                .price(new BigDecimal("20.00"))
                .stock(20)
                .build();

        Coupon coupon = Coupon.builder()
                .id(50L)
                .code("OMNIA10")
                .discountType(
                        DiscountType.PERCENTAGE
                )
                .discountValue(
                        new BigDecimal("10.00")
                )
                .minimumOrderAmount(
                        BigDecimal.ZERO
                )
                .status(CouponStatus.ACTIVE)
                .build();

        when(
                productRepository.findByIdForUpdate(10L)
        ).thenReturn(Optional.of(firstProduct));

        when(
                productRepository.findByIdForUpdate(20L)
        ).thenReturn(Optional.of(secondProduct));

        when(
                couponRepository.findByCodeForUpdate(
                        "OMNIA10"
                )
        ).thenReturn(Optional.of(coupon));

        when(
                checkoutGroupRepository.save(any())
        ).thenAnswer(invocation -> {
            CheckoutGroup group =
                    invocation.getArgument(0);

            group.setId(100L);
            group.setCheckoutReference(
                    "checkout-reference-123"
            );

            return group;
        });

        AtomicLong orderIds =
                new AtomicLong(200L);

        when(
                orderRepository.save(any())
        ).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(orderIds.getAndIncrement());
            return order;
        });

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .addressId(1000L)
                        .shippingName(
                                "Shkelqim Basha"
                        )
                        .shippingEmail(
                                "shkelqim@example.com"
                        )
                        .shippingPhone(
                                "+355690000000"
                        )
                        .shippingAddress(
                                "Tirane, Shqiperi"
                        )
                        .couponCode("omnia10")
                        .items(
                                List.of(
                                        CreateOrderItemRequest
                                                .builder()
                                                .productId(10L)
                                                .quantity(1)
                                                .build(),
                                        CreateOrderItemRequest
                                                .builder()
                                                .productId(20L)
                                                .quantity(1)
                                                .build()
                                )
                        )
                        .build();

        CheckoutResponse response =
                checkoutService.checkout(request);

        assertEquals(
                "checkout-reference-123",
                response.getCheckoutReference()
        );

        assertEquals(2, response.getOrderCount());

        assertEquals(
                new BigDecimal("60.00"),
                response.getSubtotalAmount()
        );

        assertEquals(
                new BigDecimal("7.00"),
                response.getShippingFee()
        );

        assertEquals(
                new BigDecimal("6.00"),
                response.getDiscountAmount()
        );

        assertEquals(
                new BigDecimal("61.00"),
                response.getTotalAmount()
        );

        assertEquals(2, response.getOrders().size());

        assertEquals(
                1L,
                response.getOrders()
                        .get(0)
                        .getOrganizationId()
        );

        assertEquals(
                2L,
                response.getOrders()
                        .get(1)
                        .getOrganizationId()
        );

        assertEquals(
                "checkout-reference-123",
                response.getOrders()
                        .get(0)
                        .getCheckoutReference()
        );

        assertEquals(
                "checkout-reference-123",
                response.getOrders()
                        .get(1)
                        .getCheckoutReference()
        );

        assertEquals(9, firstProduct.getStock());
        assertEquals(19, secondProduct.getStock());

        verify(
                checkoutGroupCouponRepository,
                times(1)
        ).save(
                argThat(checkoutCoupon ->
                        checkoutCoupon
                                .getDiscountAmount()
                                .compareTo(
                                        new BigDecimal("6.00")
                                ) == 0
                )
        );

        verify(
                orderRepository,
                times(2)
        ).save(any(Order.class));

        verify(
                orderStatusHistoryRepository,
                times(2)
        ).save(any(OrderStatusHistory.class));

        verify(
                paymentRepository,
                times(2)
        ).save(any(Payment.class));

        verify(
                eventPublisher,
                times(2)
        ).publishEvent(
                any(OrderStatusChangedEvent.class)
        );
    }

    @Test
    void checkout_WithProductWithoutOrganization_ShouldFail() {
        Product legacyProduct = Product.builder()
                .id(30L)
                .name("Legacy product")
                .price(new BigDecimal("10.00"))
                .stock(5)
                .organization(null)
                .build();

        when(
                productRepository.findByIdForUpdate(30L)
        ).thenReturn(Optional.of(legacyProduct));

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .shippingName("Shkelqim Basha")
                        .shippingEmail(
                                "shkelqim@example.com"
                        )
                        .shippingPhone(
                                "+355690000000"
                        )
                        .shippingAddress("Tirane")
                        .items(
                                List.of(
                                        CreateOrderItemRequest
                                                .builder()
                                                .productId(30L)
                                                .quantity(1)
                                                .build()
                                )
                        )
                        .build();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> checkoutService
                                .checkout(request)
                );

        assertTrue(
                exception.getMessage()
                        .contains(
                                "not assigned to an organization"
                        )
        );

        verify(
                checkoutGroupRepository,
                never()
        ).save(any());
    }
}