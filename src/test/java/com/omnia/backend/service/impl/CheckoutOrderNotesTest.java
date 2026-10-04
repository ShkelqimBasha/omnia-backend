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
class CheckoutOrderNotesTest {

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

    private CreateOrderRequest deliveryRequest(int companyCount) {
        for (int index = 1; index <= companyCount; index++) {
            long id = index;
            Organization company = Organization.builder().id(id).name("Company " + id)
                    .status(OrganizationStatus.ACTIVE).build();
            Product product = Product.builder().id(id).name("Product " + id).organization(company)
                    .price(new BigDecimal("100.00")).stock(10).build();
            when(productRepository.findByIdForUpdate(id)).thenReturn(Optional.of(product));
        }
        when(checkoutGroupRepository.save(any())).thenAnswer(call -> {
            CheckoutGroup group = call.getArgument(0); group.setId(100L); return group;
        });
        AtomicLong ids = new AtomicLong(1L);
        when(orderRepository.save(any())).thenAnswer(call -> {
            Order order = call.getArgument(0); order.setId(ids.getAndIncrement()); return order;
        });
        return CreateOrderRequest.builder().shippingName("Buyer").shippingEmail("buyer@example.com")
                .shippingPhone("123").shippingAddress("Street, Building 1, Tirana")
                .items(java.util.stream.IntStream.rangeClosed(1, companyCount)
                        .mapToObj(index -> CreateOrderItemRequest.builder().productId((long) index).quantity(1).build()).toList())
                .deliveryMethod(com.omnia.backend.enums.DeliveryMethod.EXPRESS_24H)
                .expectedExpressSurcharge(new BigDecimal("5.00"))
                .shippingLatitude(new BigDecimal("41.3275000"))
                .shippingLongitude(new BigDecimal("19.8187000")).build();
    }

    @Test void deliveryNoteIsSavedForEveryCompanyOrderWithoutChangingPricing() {
        CreateOrderRequest request = deliveryRequest(2);
        request.setOrderNotes("  Telefononi para dërgesës. Kati 3.  ");
        CheckoutResponse result = checkoutService.checkout(request);
        assertEquals(new BigDecimal("205.00"), result.getTotalAmount());
        assertEquals(2, result.getOrders().size());
        for (var order : result.getOrders())
            assertEquals("Telefononi para dërgesës. Kati 3.", order.getOrderNotes());
    }

    @Test void omittedNoteDoesNotPreventCheckout() {
        CheckoutResponse result = checkoutService.checkout(deliveryRequest(1));
        assertNull(result.getOrders().get(0).getOrderNotes());
    }
}
