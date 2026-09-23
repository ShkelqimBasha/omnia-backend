package com.omnia.backend.service.impl;

import com.omnia.backend.dto.request.CreateOrderItemRequest;
import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.dto.response.CheckoutResponse;
import com.omnia.backend.entity.CheckoutGroup;
import com.omnia.backend.entity.Coupon;
import com.omnia.backend.entity.Order;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CouponStatus;
import com.omnia.backend.enums.DiscountType;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.repository.CheckoutGroupCouponRepository;
import com.omnia.backend.repository.CheckoutGroupRepository;
import com.omnia.backend.repository.CouponRepository;
import com.omnia.backend.repository.OrderCouponRepository;
import com.omnia.backend.repository.OrderItemRepository;
import com.omnia.backend.repository.OrderRepository;
import com.omnia.backend.repository.OrderStatusHistoryRepository;
import com.omnia.backend.repository.PaymentRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.UserRepository;
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

@ExtendWith(MockitoExtension.class)
class OrganizationScopedCouponCheckoutTest {

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
    private Organization firstOrganization;
    private Organization secondOrganization;

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

        firstOrganization = Organization.builder()
                .id(10L)
                .name("Omnia Store")
                .slug("omnia-store")
                .status(OrganizationStatus.ACTIVE)
                .build();

        secondOrganization = Organization.builder()
                .id(20L)
                .name("Cimi Test")
                .slug("cimi-test")
                .status(OrganizationStatus.ACTIVE)
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        currentUser.getEmail(),
                        null,
                        AuthorityUtils
                                .createAuthorityList(
                                        "ROLE_USER"
                                )
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        lenient()
                .when(
                        userRepository.findByEmail(
                                currentUser.getEmail()
                        )
                )
                .thenReturn(Optional.of(currentUser));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void organizationCoupon_ShouldDiscountOnlyOwnerOrder() {
        Product firstProduct =
                createProduct(
                        100L,
                        "Telefon",
                        firstOrganization,
                        "40.00"
                );

        Product secondProduct =
                createProduct(
                        200L,
                        "Rimel",
                        secondOrganization,
                        "20.00"
                );

        Coupon coupon =
                createOrganizationCoupon(
                        firstOrganization
                );

        when(productRepository
                .findByIdForUpdate(100L))
                .thenReturn(
                        Optional.of(firstProduct)
                );

        when(productRepository
                .findByIdForUpdate(200L))
                .thenReturn(
                        Optional.of(secondProduct)
                );

        when(couponRepository
                .findByCodeForUpdate("ORG10"))
                .thenReturn(Optional.of(coupon));

        when(checkoutGroupRepository
                .save(any(CheckoutGroup.class)))
                .thenAnswer(invocation -> {
                    CheckoutGroup group =
                            invocation.getArgument(0);

                    group.setId(500L);
                    group.setCheckoutReference(
                            "checkout-org-coupon"
                    );

                    return group;
                });

        AtomicLong orderIds =
                new AtomicLong(1000L);

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order =
                            invocation.getArgument(0);

                    order.setId(
                            orderIds.getAndIncrement()
                    );

                    return order;
                });

        CheckoutResponse response =
                checkoutService.checkout(
                        createRequest(
                                List.of(
                                        createItem(100L),
                                        createItem(200L)
                                )
                        )
                );

        assertEquals(
                0,
                new BigDecimal("4.00")
                        .compareTo(
                                response.getDiscountAmount()
                        )
        );

        assertEquals(
                0,
                new BigDecimal("63.00")
                        .compareTo(
                                response.getTotalAmount()
                        )
        );

        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(
                        Order.class
                );

        verify(orderRepository, times(2))
                .save(orderCaptor.capture());

        List<Order> orders =
                orderCaptor.getAllValues();

        Order firstOrder = orders.get(0);
        Order secondOrder = orders.get(1);

        assertEquals(
                firstOrganization.getId(),
                firstOrder.getOrganization().getId()
        );

        assertEquals(
                0,
                new BigDecimal("4.00")
                        .compareTo(
                                firstOrder
                                        .getDiscountAmount()
                        )
        );

        assertEquals(
                "ORG10",
                firstOrder.getCouponCode()
        );

        assertEquals(
                secondOrganization.getId(),
                secondOrder.getOrganization().getId()
        );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        secondOrder
                                .getDiscountAmount()
                )
        );

        assertNull(
                secondOrder.getCouponCode()
        );
    }

    @Test
    void organizationCoupon_ShouldRejectCheckoutWithoutOwnerProducts() {
        Product secondProduct =
                createProduct(
                        200L,
                        "Rimel",
                        secondOrganization,
                        "20.00"
                );

        Coupon coupon =
                createOrganizationCoupon(
                        firstOrganization
                );

        when(productRepository
                .findByIdForUpdate(200L))
                .thenReturn(
                        Optional.of(secondProduct)
                );

        when(couponRepository
                .findByCodeForUpdate("ORG10"))
                .thenReturn(Optional.of(coupon));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> checkoutService.checkout(
                                createRequest(
                                        List.of(
                                                createItem(200L)
                                        )
                                )
                        )
                );

        assertEquals(
                "Coupon does not apply to products "
                        + "in this checkout",
                exception.getMessage()
        );

        verify(checkoutGroupRepository, never())
                .save(any());
    }

    private Product createProduct(
            Long id,
            String name,
            Organization organization,
            String price
    ) {
        return Product.builder()
                .id(id)
                .name(name)
                .organization(organization)
                .price(new BigDecimal(price))
                .stock(10)
                .build();
    }

    private Coupon createOrganizationCoupon(
            Organization organization
    ) {
        return Coupon.builder()
                .id(50L)
                .organization(organization)
                .code("ORG10")
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
    }

    private CreateOrderItemRequest createItem(
            Long productId
    ) {
        return CreateOrderItemRequest.builder()
                .productId(productId)
                .quantity(1)
                .build();
    }

    private CreateOrderRequest createRequest(
            List<CreateOrderItemRequest> items
    ) {
        return CreateOrderRequest.builder()
                .shippingName("Shkelqim Basha")
                .shippingEmail(
                        "shkelqim@example.com"
                )
                .shippingPhone(
                        "+355690000000"
                )
                .shippingAddress(
                        "Tirane, Shqiperi"
                )
                .couponCode("org10")
                .items(items)
                .build();
    }
}