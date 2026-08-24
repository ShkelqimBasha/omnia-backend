package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.CreateOrderItemRequest;
import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.dto.response.OrderResponse;
import com.omnia.backend.dto.response.OrderStatusHistoryResponse;
import com.omnia.backend.entity.Order;
import com.omnia.backend.entity.OrderItem;
import com.omnia.backend.entity.OrderStatusHistory;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.User;
import com.omnia.backend.entity.Payment;
import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.enums.PaymentStatus;
import com.omnia.backend.enums.OrderStatus;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.ApplicationEventPublisher;
import com.omnia.backend.event.OrderStatusChangedEvent;
import com.omnia.backend.repository.CouponRepository;
import com.omnia.backend.repository.OrderCouponRepository;
import com.omnia.backend.entity.Coupon;
import com.omnia.backend.enums.CouponStatus;
import com.omnia.backend.enums.DiscountType;
import com.omnia.backend.entity.OrderCoupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusHistoryRepository
            orderStatusHistoryRepository;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private OrderCouponRepository
            orderCouponRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User currentUser;
    private User anotherUser;

    private Product discountedProduct;
    private Product regularProduct;

    private CreateOrderItemRequest firstItemRequest;
    private CreateOrderRequest createOrderRequest;

    private Order initialSavedOrder;
    private Order finalSavedOrder;

    @BeforeEach
    void setUp() {

        currentUser = User.builder()
                .id(1L)
                .username("shkelqim")
                .email("shkelqim@example.com")
                .build();

        anotherUser = User.builder()
                .id(2L)
                .username("anotherUser")
                .email("another@example.com")
                .build();

        discountedProduct = Product.builder()
                .id(10L)
                .name("Samsung Galaxy S24")
                .price(new BigDecimal("1200.00"))
                .discountPrice(new BigDecimal("1000.00"))
                .stock(100)
                .build();

        regularProduct = Product.builder()
                .id(20L)
                .name("Laptop Lenovo")
                .price(new BigDecimal("800.00"))
                .discountPrice(null)
                .stock(100)
                .build();

        firstItemRequest = CreateOrderItemRequest.builder()
                .productId(10L)
                .quantity(2)
                .build();

        createOrderRequest = CreateOrderRequest.builder()
                .addressId(100L)
                .shippingName("Shkelqim Basha")
                .shippingEmail("shkelqimbasha8@gmail.com")
                .shippingPhone("+355686574870")
                .shippingAddress("Tirane, Shqiperi")
                .items(List.of(firstItemRequest))
                .build();

        initialSavedOrder = Order.builder()
                .id(50L)
                .user(currentUser)
                .addressId(100L)
                .totalAmount(BigDecimal.ZERO)
                .status(OrderStatus.PENDING)
                .build();

        finalSavedOrder = Order.builder()
                .id(50L)
                .user(currentUser)
                .addressId(100L)
                .totalAmount(new BigDecimal("2000.00"))
                .status(OrderStatus.PENDING)
                .createdAt(
                        LocalDateTime.of(
                                2026,
                                7,
                                16,
                                20,
                                0
                        )
                )
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
        lenient()
                .when(
                        userRepository.findByEmail(
                                "shkelqim@example.com"
                        )
                )
                .thenReturn(
                        Optional.of(currentUser)
                );
        lenient()
                .when(
                        couponRepository.findByCodeForUpdate(
                                "OMNIA10"
                        )
                )
                .thenReturn(
                        Optional.of(
                                Coupon.builder()
                                        .id(201L)
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
                                        .build()
                        )
                );

        lenient()
                .when(
                        couponRepository.findByCodeForUpdate(
                                "WELCOME5"
                        )
                )
                .thenReturn(
                        Optional.of(
                                Coupon.builder()
                                        .id(202L)
                                        .code("WELCOME5")
                                        .discountType(
                                                DiscountType.FIXED
                                        )
                                        .discountValue(
                                                new BigDecimal("5.00")
                                        )
                                        .minimumOrderAmount(
                                                BigDecimal.ZERO
                                        )
                                        .perUserLimit(1)
                                        .status(CouponStatus.ACTIVE)
                                        .build()
                        )
                );

        lenient()
                .when(
                        couponRepository.findByCodeForUpdate(
                                "FREE"
                        )
                )
                .thenReturn(
                        Optional.of(
                                Coupon.builder()
                                        .id(203L)
                                        .code("FREE")
                                        .discountType(
                                                DiscountType.FREE_SHIPPING
                                        )
                                        .discountValue(
                                                BigDecimal.ZERO
                                        )
                                        .minimumOrderAmount(
                                                BigDecimal.ZERO
                                        )
                                        .status(CouponStatus.ACTIVE)
                                        .build()
                        )
                );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createOrder_shouldCreateOrderWithDiscountedProduct() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.save(any(Order.class)))
                .thenReturn(
                        initialSavedOrder,
                        finalSavedOrder
                );

        when(productRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(discountedProduct));

        when(orderItemRepository.saveAll(
                any()
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        OrderResponse result =
                orderService.createOrder(createOrderRequest);

        assertNotNull(result);
        assertEquals(50L, result.getId());
        assertEquals(1L, result.getUserId());
        assertEquals(100L, result.getAddressId());
        assertEquals(
                OrderStatus.PENDING,
                result.getStatus()
        );
        assertEquals(
                PaymentMethod.CASH_ON_DELIVERY,
                result.getPaymentMethod()
        );
        assertEquals(
                PaymentStatus.PENDING,
                result.getPaymentStatus()
        );
        assertNull(result.getPaidAt());
        assertNull(result.getTransactionId());
        assertEquals(
                0,
                new BigDecimal("2000.00")
                        .compareTo(result.getTotalAmount())
        );

        assertNotNull(result.getItems());
        assertEquals(1, result.getItems().size());

        assertEquals(
                10L,
                result.getItems().getFirst().getProductId()
        );
        assertEquals(
                "Samsung Galaxy S24",
                result.getItems().getFirst().getProductName()
        );
        assertEquals(
                2,
                result.getItems().getFirst().getQuantity()
        );
        assertEquals(
                0,
                new BigDecimal("1000.00")
                        .compareTo(
                                result.getItems()
                                        .getFirst()
                                        .getUnitPrice()
                        )
        );
        assertEquals(
                0,
                new BigDecimal("2000.00")
                        .compareTo(
                                result.getItems()
                                        .getFirst()
                                        .getSubtotal()
                        )
        );

        verify(productRepository).findByIdForUpdate(10L);
        verify(orderItemRepository).saveAll(any());
        verify(orderRepository, times(2))
                .save(any(Order.class));

        ArgumentCaptor<Payment> paymentCaptor =
                ArgumentCaptor.forClass(Payment.class);

        verify(paymentRepository).save(paymentCaptor.capture());

        Payment savedPayment = paymentCaptor.getValue();

        assertSame(finalSavedOrder, savedPayment.getOrder());
        assertEquals(
                PaymentMethod.CASH_ON_DELIVERY,
                savedPayment.getMethod()
        );
        assertEquals(
                PaymentStatus.PENDING,
                savedPayment.getStatus()
        );
        assertNull(savedPayment.getTransactionId());
        assertNull(savedPayment.getPaidAt());
        ArgumentCaptor<OrderStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        OrderStatusChangedEvent.class
                );

        verify(eventPublisher)
                .publishEvent(
                        eventCaptor.capture()
                );

        OrderStatusChangedEvent event =
                eventCaptor.getValue();

        assertEquals(
                "shkelqim@example.com",
                event.recipientEmail()
        );
        assertEquals(
                "shkelqim",
                event.recipientName()
        );
        assertEquals(50L, event.orderId());
        assertEquals(
                OrderStatus.PENDING,
                event.status()
        );
        assertEquals(
                0,
                new BigDecimal("2000.00")
                        .compareTo(event.totalAmount())
        );
    }

    @Test
    void createOrder_shouldUseNormalPriceWhenDiscountIsMissing() {

        CreateOrderItemRequest regularItemRequest =
                CreateOrderItemRequest.builder()
                        .productId(20L)
                        .quantity(3)
                        .build();

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .addressId(100L)
                        .shippingName("Shkelqim Basha")
                        .shippingEmail("shkelqimbasha8@gmail.com")
                        .shippingPhone("+355686574870")
                        .shippingAddress("Tirane, Shqiperi")
                        .items(List.of(regularItemRequest))
                        .build();

        Order finalOrder = Order.builder()
                .id(51L)
                .user(currentUser)
                .addressId(100L)
                .totalAmount(new BigDecimal("2400.00"))
                .status(OrderStatus.PENDING)
                .build();

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.save(any(Order.class)))
                .thenReturn(
                        initialSavedOrder,
                        finalOrder
                );

        when(productRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(regularProduct));

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        OrderResponse result =
                orderService.createOrder(request);

        assertNotNull(result);
        assertEquals(
                0,
                new BigDecimal("2400.00")
                        .compareTo(result.getTotalAmount())
        );

        assertEquals(
                0,
                new BigDecimal("800.00")
                        .compareTo(
                                result.getItems()
                                        .getFirst()
                                        .getUnitPrice()
                        )
        );

        assertEquals(
                0,
                new BigDecimal("2400.00")
                        .compareTo(
                                result.getItems()
                                        .getFirst()
                                        .getSubtotal()
                        )
        );
    }

    @Test
    void createOrder_shouldBuildOrderItemCorrectly() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.save(any(Order.class)))
                .thenReturn(
                        initialSavedOrder,
                        finalSavedOrder
                );

        when(productRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(discountedProduct));

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        orderService.createOrder(createOrderRequest);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderItem>> itemsCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(orderItemRepository)
                .saveAll(itemsCaptor.capture());

        List<OrderItem> savedItems =
                itemsCaptor.getValue();

        assertEquals(1, savedItems.size());

        OrderItem savedItem =
                savedItems.getFirst();

        assertEquals(initialSavedOrder, savedItem.getOrder());
        assertEquals(10L, savedItem.getProductId());
        assertEquals(
                "Samsung Galaxy S24",
                savedItem.getProductName()
        );
        assertNull(savedItem.getProductImage());
        assertNull(savedItem.getVariantInfo());

        assertEquals(
                0,
                new BigDecimal("1000.00")
                        .compareTo(savedItem.getUnitPrice())
        );
        assertEquals(2, savedItem.getQuantity());
        assertEquals(
                0,
                new BigDecimal("2000.00")
                        .compareTo(savedItem.getSubtotal())
        );
    }

    @Test
    void createOrder_shouldInitializeOrderAsPending() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.save(any(Order.class)))
                .thenReturn(
                        initialSavedOrder,
                        finalSavedOrder
                );

        when(productRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(discountedProduct));

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        orderService.createOrder(createOrderRequest);

        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository, times(2))
                .save(orderCaptor.capture());

        Order firstSavedOrder =
                orderCaptor.getAllValues().getFirst();

        assertEquals(currentUser, firstSavedOrder.getUser());
        assertEquals(100L, firstSavedOrder.getAddressId());
        assertEquals(
                OrderStatus.PENDING,
                firstSavedOrder.getStatus()
        );
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        firstSavedOrder.getTotalAmount()
                )
        );
    }

    @Test
    void createOrder_shouldUpdateFinalTotalAmount() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order saved = invocation.getArgument(0);
                    saved.setId(50L);
                    return saved;
                });

        when(productRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(discountedProduct));

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        OrderResponse result =
                orderService.createOrder(createOrderRequest);

        assertEquals(
                0,
                new BigDecimal("2000.00")
                        .compareTo(result.getTotalAmount())
        );

        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository, times(2))
                .save(orderCaptor.capture());

        Order finalOrder =
                orderCaptor.getAllValues().get(1);

        assertEquals(
                0,
                new BigDecimal("2000.00")
                        .compareTo(finalOrder.getTotalAmount())
        );
    }

    @Test
    void createOrder_shouldThrowWhenProductDoesNotExist() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.save(any(Order.class)))
                .thenReturn(initialSavedOrder);

        when(productRepository.findByIdForUpdate(99L))
                .thenReturn(Optional.empty());

        CreateOrderItemRequest missingProductItem =
                CreateOrderItemRequest.builder()
                        .productId(99L)
                        .quantity(1)
                        .build();

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .addressId(100L)
                        .shippingName("Shkelqim Basha")
                        .shippingEmail("shkelqimbasha8@gmail.com")
                        .shippingPhone("+355686574870")
                        .shippingAddress("Tirane, Shqiperi")
                        .items(List.of(missingProductItem))
                        .build();

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> orderService.createOrder(request)
                );

        assertEquals(
                "Product not found",
                exception.getMessage()
        );

        verify(orderItemRepository, never())
                .saveAll(any());

        /*
         * Order-i fillestar ruhet para kërkimit të produktit.
         * Në ekzekutim real transaksioni do të bëjë rollback.
         */
        verify(orderRepository, times(1))
                .save(any(Order.class));
    }
    @Test
    void createOrder_shouldCreateOrderWithMultipleProducts() {

        CreateOrderItemRequest secondItemRequest =
                CreateOrderItemRequest.builder()
                        .productId(20L)
                        .quantity(1)
                        .build();

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .addressId(100L)
                        .shippingName("Shkelqim Basha")
                        .shippingEmail("shkelqimbasha8@gmail.com")
                        .shippingPhone("+355686574870")
                        .shippingAddress("Tirane, Shqiperi")
                        .items(List.of(
                                firstItemRequest,
                                secondItemRequest
                        ))
                        .build();

        Order completedOrder = Order.builder()
                .id(50L)
                .user(currentUser)
                .addressId(100L)
                .totalAmount(new BigDecimal("2800.00"))
                .status(OrderStatus.PENDING)
                .build();

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.save(any(Order.class)))
                .thenReturn(
                        initialSavedOrder,
                        completedOrder
                );

        when(productRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(discountedProduct));

        when(productRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(regularProduct));

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        OrderResponse result =
                orderService.createOrder(request);

        assertNotNull(result);
        assertEquals(2, result.getItems().size());

        assertEquals(
                0,
                new BigDecimal("2800.00")
                        .compareTo(result.getTotalAmount())
        );

        assertEquals(
                "Samsung Galaxy S24",
                result.getItems().get(0).getProductName()
        );

        assertEquals(
                "Laptop Lenovo",
                result.getItems().get(1).getProductName()
        );

        assertEquals(
                0,
                new BigDecimal("2000.00")
                        .compareTo(
                                result.getItems()
                                        .get(0)
                                        .getSubtotal()
                        )
        );

        assertEquals(
                0,
                new BigDecimal("800.00")
                        .compareTo(
                                result.getItems()
                                        .get(1)
                                        .getSubtotal()
                        )
        );

        verify(productRepository).findByIdForUpdate(10L);
        verify(productRepository).findByIdForUpdate(20L);
    }

    @Test
    void createOrder_shouldSaveAllOrderItemsTogether() {

        CreateOrderItemRequest secondItemRequest =
                CreateOrderItemRequest.builder()
                        .productId(20L)
                        .quantity(1)
                        .build();

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .addressId(100L)
                        .shippingName("Shkelqim Basha")
                        .shippingEmail("shkelqimbasha8@gmail.com")
                        .shippingPhone("+355686574870")
                        .shippingAddress("Tirane, Shqiperi")
                        .items(List.of(
                                firstItemRequest,
                                secondItemRequest
                        ))
                        .build();

        Order completedOrder = Order.builder()
                .id(50L)
                .user(currentUser)
                .addressId(100L)
                .totalAmount(new BigDecimal("2800.00"))
                .status(OrderStatus.PENDING)
                .build();

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.save(any(Order.class)))
                .thenReturn(
                        initialSavedOrder,
                        completedOrder
                );

        when(productRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(discountedProduct));

        when(productRepository.findByIdForUpdate(20L))
                .thenReturn(Optional.of(regularProduct));

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        orderService.createOrder(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderItem>> itemsCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(orderItemRepository)
                .saveAll(itemsCaptor.capture());

        List<OrderItem> savedItems =
                itemsCaptor.getValue();

        assertEquals(2, savedItems.size());

        OrderItem firstItem = savedItems.get(0);
        OrderItem secondItem = savedItems.get(1);

        assertEquals(10L, firstItem.getProductId());
        assertEquals(2, firstItem.getQuantity());
        assertEquals(
                0,
                new BigDecimal("1000.00")
                        .compareTo(firstItem.getUnitPrice())
        );
        assertEquals(
                0,
                new BigDecimal("2000.00")
                        .compareTo(firstItem.getSubtotal())
        );

        assertEquals(20L, secondItem.getProductId());
        assertEquals(1, secondItem.getQuantity());
        assertEquals(
                0,
                new BigDecimal("800.00")
                        .compareTo(secondItem.getUnitPrice())
        );
        assertEquals(
                0,
                new BigDecimal("800.00")
                        .compareTo(secondItem.getSubtotal())
        );
    }

    @Test
    void getMyOrders_shouldReturnCurrentUsersOrders() {

        Order firstOrder = Order.builder()
                .id(50L)
                .user(currentUser)
                .addressId(100L)
                .totalAmount(new BigDecimal("2000.00"))
                .status(OrderStatus.PENDING)
                .createdAt(
                        LocalDateTime.of(
                                2026,
                                7,
                                16,
                                20,
                                0
                        )
                )
                .build();

        Order secondOrder = Order.builder()
                .id(51L)
                .user(currentUser)
                .addressId(101L)
                .totalAmount(new BigDecimal("800.00"))
                .status(OrderStatus.PENDING)
                .createdAt(
                        LocalDateTime.of(
                                2026,
                                7,
                                16,
                                21,
                                0
                        )
                )
                .build();
        Payment firstPayment = Payment.builder()
                .id(60L)
                .order(firstOrder)
                .method(PaymentMethod.CASH_ON_DELIVERY)
                .status(PaymentStatus.PENDING)
                .build();


        OrderItem firstOrderItem = OrderItem.builder()
                .id(1L)
                .order(firstOrder)
                .productId(10L)
                .productName("Samsung Galaxy S24")
                .unitPrice(new BigDecimal("1000.00"))
                .quantity(2)
                .subtotal(new BigDecimal("2000.00"))
                .build();

        OrderItem secondOrderItem = OrderItem.builder()
                .id(2L)
                .order(secondOrder)
                .productId(20L)
                .productName("Laptop Lenovo")
                .unitPrice(new BigDecimal("800.00"))
                .quantity(1)
                .subtotal(new BigDecimal("800.00"))
                .build();

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.findByUserId(1L))
                .thenReturn(List.of(firstOrder, secondOrder));

        when(paymentRepository.findByOrderId(50L))
                .thenReturn(Optional.of(firstPayment));

        when(paymentRepository.findByOrderId(51L))
                .thenReturn(Optional.empty());

        when(orderItemRepository.findByOrderId(50L))
                .thenReturn(List.of(firstOrderItem));

        when(orderItemRepository.findByOrderId(51L))
                .thenReturn(List.of(secondOrderItem));

        List<OrderResponse> result =
                orderService.getMyOrders();

        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals(50L, result.get(0).getId());
        assertEquals(51L, result.get(1).getId());
        assertEquals(
                PaymentMethod.CASH_ON_DELIVERY,
                result.get(0).getPaymentMethod()
        );
        assertEquals(
                PaymentStatus.PENDING,
                result.get(0).getPaymentStatus()
        );
        assertNull(result.get(0).getPaidAt());
        assertNull(result.get(0).getTransactionId());

        assertNull(result.get(1).getPaymentMethod());
        assertNull(result.get(1).getPaymentStatus());

        assertEquals(
                "Samsung Galaxy S24",
                result.get(0)
                        .getItems()
                        .getFirst()
                        .getProductName()
        );

        assertEquals(
                "Laptop Lenovo",
                result.get(1)
                        .getItems()
                        .getFirst()
                        .getProductName()
        );

        verify(orderRepository).findByUserId(1L);
        verify(orderItemRepository).findByOrderId(50L);
        verify(orderItemRepository).findByOrderId(51L);
    }

    @Test
    void getMyOrders_shouldReturnEmptyList() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.findByUserId(1L))
                .thenReturn(List.of());

        List<OrderResponse> result =
                orderService.getMyOrders();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(orderRepository).findByUserId(1L);

        verify(orderItemRepository, never())
                .findByOrderId(anyLong());
    }

    @Test
    void getOrderById_shouldReturnOrderSuccessfully() {

        Order order = Order.builder()
                .id(50L)
                .user(currentUser)
                .addressId(100L)
                .totalAmount(new BigDecimal("2000.00"))
                .status(OrderStatus.PENDING)
                .createdAt(
                        LocalDateTime.of(
                                2026,
                                7,
                                16,
                                20,
                                0
                        )
                )
                .build();

        OrderItem item = OrderItem.builder()
                .id(1L)
                .order(order)
                .productId(10L)
                .productName("Samsung Galaxy S24")
                .unitPrice(new BigDecimal("1000.00"))
                .quantity(2)
                .subtotal(new BigDecimal("2000.00"))
                .build();

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.findById(50L))
                .thenReturn(Optional.of(order));

        when(orderItemRepository.findByOrderId(50L))
                .thenReturn(List.of(item));

        OrderResponse result =
                orderService.getOrderById(50L);

        assertNotNull(result);
        assertEquals(50L, result.getId());
        assertEquals(1L, result.getUserId());
        assertEquals(100L, result.getAddressId());
        assertEquals(
                OrderStatus.PENDING,
                result.getStatus()
        );

        assertEquals(
                0,
                new BigDecimal("2000.00")
                        .compareTo(result.getTotalAmount())
        );

        assertEquals(1, result.getItems().size());
        assertEquals(
                "Samsung Galaxy S24",
                result.getItems()
                        .getFirst()
                        .getProductName()
        );

        verify(orderRepository).findById(50L);
        verify(orderItemRepository).findByOrderId(50L);
    }

    @Test
    void getOrderById_shouldReturnOrderWithEmptyItems() {

        Order order = Order.builder()
                .id(50L)
                .user(currentUser)
                .addressId(100L)
                .totalAmount(BigDecimal.ZERO)
                .status(OrderStatus.PENDING)
                .build();

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.findById(50L))
                .thenReturn(Optional.of(order));

        when(orderItemRepository.findByOrderId(50L))
                .thenReturn(List.of());

        OrderResponse result =
                orderService.getOrderById(50L);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.getTotalAmount()
                )
        );
    }

    @Test
    void getOrderById_shouldThrowWhenOrderDoesNotExist() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> orderService.getOrderById(99L)
                );

        assertEquals(
                "Order not found",
                exception.getMessage()
        );

        verify(orderItemRepository, never())
                .findByOrderId(anyLong());
    }
    @Test
    void getOrderById_shouldThrowWhenUserIsNotOwner() {

        Order order = Order.builder()
                .id(50L)
                .user(anotherUser)
                .addressId(100L)
                .totalAmount(new BigDecimal("2000.00"))
                .status(OrderStatus.PENDING)
                .build();

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.findById(50L))
                .thenReturn(Optional.of(order));

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> orderService.getOrderById(50L)
                );

        assertEquals(
                "You are not allowed to access this order",
                exception.getMessage()
        );

        verify(orderItemRepository, never())
                .findByOrderId(anyLong());
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
                .thenReturn(Optional.of(currentUser));

        when(orderRepository.findByUserId(1L))
                .thenReturn(List.of());

        List<OrderResponse> result =
                orderService.getMyOrders();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(userRepository)
                .findByUsername("shkelqim");
    }

    @Test
    void getCurrentUser_shouldThrowWhenUserDoesNotExist() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.empty());

        when(userRepository.findByUsername(
                "shkelqim@example.com"
        )).thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        orderService::getMyOrders
                );

        assertEquals(
                "User not found",
                exception.getMessage()
        );

        verifyNoInteractions(
                orderRepository,
                orderItemRepository,
                productRepository
        );
    }

    @Test
    void createOrder_shouldCreateEmptyOrderWhenItemsListIsEmpty() {

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .addressId(100L)
                        .shippingName("Shkelqim Basha")
                        .shippingEmail("shkelqimbasha8@gmail.com")
                        .shippingPhone("+355686574870")
                        .shippingAddress("Tirane, Shqiperi")
                        .items(List.of())
                        .build();

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(50L);
                    return savedOrder;
                });

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        OrderResponse result =
                orderService.createOrder(request);

        assertNotNull(result);
        assertEquals(50L, result.getId());
        assertTrue(result.getItems().isEmpty());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.getTotalAmount()
                )
        );

        verifyNoInteractions(productRepository);

        verify(orderItemRepository)
                .saveAll(any());
    }

    @Test
    void createOrder_shouldCalculateQuantityCorrectly() {

        CreateOrderItemRequest itemRequest =
                CreateOrderItemRequest.builder()
                        .productId(10L)
                        .quantity(4)
                        .build();

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .addressId(100L)
                        .shippingName("Shkelqim Basha")
                        .shippingEmail("shkelqimbasha8@gmail.com")
                        .shippingPhone("+355686574870")
                        .shippingAddress("Tirane, Shqiperi")
                        .items(List.of(itemRequest))
                        .build();

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(50L);
                    return savedOrder;
                });

        when(productRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(discountedProduct));

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        OrderResponse result =
                orderService.createOrder(request);

        assertEquals(1, result.getItems().size());
        assertEquals(
                4,
                result.getItems().getFirst().getQuantity()
        );

        assertEquals(
                0,
                new BigDecimal("4000.00")
                        .compareTo(
                                result.getItems()
                                        .getFirst()
                                        .getSubtotal()
                        )
        );

        assertEquals(
                0,
                new BigDecimal("4000.00")
                        .compareTo(result.getTotalAmount())
        );
    }

    @Test
    void createOrder_shouldKeepAddressId() {

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .addressId(999L)
                        .shippingName("Shkelqim Basha")
                        .shippingEmail("shkelqimbasha8@gmail.com")
                        .shippingPhone("+355686574870")
                        .shippingAddress("Tirane, Shqiperi")
                        .items(List.of(firstItemRequest))
                        .build();

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(50L);
                    return savedOrder;
                });

        when(productRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(discountedProduct));

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        OrderResponse result =
                orderService.createOrder(request);

        assertEquals(999L, result.getAddressId());

        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository, times(2))
                .save(orderCaptor.capture());

        assertEquals(
                999L,
                orderCaptor.getAllValues()
                        .getFirst()
                        .getAddressId()
        );
    }

    @Test
    void createOrder_shouldAssociateOrderItemsWithSavedOrder() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.save(any(Order.class)))
                .thenReturn(
                        initialSavedOrder,
                        finalSavedOrder
                );

        when(productRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(discountedProduct));

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        orderService.createOrder(createOrderRequest);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderItem>> itemsCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(orderItemRepository)
                .saveAll(itemsCaptor.capture());

        List<OrderItem> items =
                itemsCaptor.getValue();

        assertEquals(1, items.size());
        assertSame(
                initialSavedOrder,
                items.getFirst().getOrder()
        );
    }

    @Test
    void getMyOrders_shouldMapEachOrdersItemsSeparately() {

        Order firstOrder = Order.builder()
                .id(50L)
                .user(currentUser)
                .addressId(100L)
                .totalAmount(new BigDecimal("2000.00"))
                .status(OrderStatus.PENDING)
                .build();

        Order secondOrder = Order.builder()
                .id(51L)
                .user(currentUser)
                .addressId(101L)
                .totalAmount(new BigDecimal("800.00"))
                .status(OrderStatus.PENDING)
                .build();

        OrderItem firstItem = OrderItem.builder()
                .id(1L)
                .order(firstOrder)
                .productId(10L)
                .productName("Samsung Galaxy S24")
                .unitPrice(new BigDecimal("1000.00"))
                .quantity(2)
                .subtotal(new BigDecimal("2000.00"))
                .build();

        OrderItem secondItem = OrderItem.builder()
                .id(2L)
                .order(secondOrder)
                .productId(20L)
                .productName("Laptop Lenovo")
                .unitPrice(new BigDecimal("800.00"))
                .quantity(1)
                .subtotal(new BigDecimal("800.00"))
                .build();

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.findByUserId(1L))
                .thenReturn(List.of(firstOrder, secondOrder));

        when(orderItemRepository.findByOrderId(50L))
                .thenReturn(List.of(firstItem));

        when(orderItemRepository.findByOrderId(51L))
                .thenReturn(List.of(secondItem));

        List<OrderResponse> result =
                orderService.getMyOrders();

        assertEquals(2, result.size());

        assertEquals(
                10L,
                result.get(0)
                        .getItems()
                        .getFirst()
                        .getProductId()
        );

        assertEquals(
                20L,
                result.get(1)
                        .getItems()
                        .getFirst()
                        .getProductId()
        );

        verify(orderItemRepository, times(1))
                .findByOrderId(50L);

        verify(orderItemRepository, times(1))
                .findByOrderId(51L);
    }
    @Test
    void createOrder_shouldCalculateShippingAndFinalTotal() {

        Product product = Product.builder()
                .id(30L)
                .name("Test Product")
                .price(new BigDecimal("35.90"))
                .discountPrice(null)
                .stock(10)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingName("Test User")
                .shippingEmail("test@example.com")
                .shippingPhone("+355690000000")
                .shippingAddress("Tirane, Shqiperi")
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .productId(30L)
                                .quantity(1)
                                .build()
                ))
                .build();

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        when(productRepository.findByIdForUpdate(30L))
                .thenReturn(Optional.of(product));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(60L);
                    return savedOrder;
                });

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.createOrder(request);

        assertEquals(
                0,
                new BigDecimal("35.90")
                        .compareTo(result.getSubtotalAmount())
        );

        assertEquals(
                0,
                new BigDecimal("3.50")
                        .compareTo(result.getShippingFee())
        );

        assertEquals(
                0,
                BigDecimal.ZERO
                        .compareTo(result.getDiscountAmount())
        );

        assertEquals(
                0,
                new BigDecimal("39.40")
                        .compareTo(result.getTotalAmount())
        );

        assertNull(result.getCouponCode());
    }
    @Test
    void createOrder_shouldApplyOmnia10Coupon() {

        Product product = Product.builder()
                .id(31L)
                .name("Coupon Test Product")
                .price(new BigDecimal("35.90"))
                .discountPrice(null)
                .stock(10)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingName("Test User")
                .shippingEmail("test@example.com")
                .shippingPhone("+355690000000")
                .shippingAddress("Tirane, Shqiperi")
                .couponCode(" omnia10 ")
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .productId(31L)
                                .quantity(1)
                                .build()
                ))
                .build();

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        when(productRepository.findByIdForUpdate(31L))
                .thenReturn(Optional.of(product));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(61L);
                    return savedOrder;
                });

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.createOrder(request);

        assertEquals("OMNIA10", result.getCouponCode());

        assertEquals(
                0,
                new BigDecimal("3.59")
                        .compareTo(result.getDiscountAmount())
        );

        assertEquals(
                0,
                new BigDecimal("3.50")
                        .compareTo(result.getShippingFee())
        );

        assertEquals(
                0,
                new BigDecimal("35.81")
                        .compareTo(result.getTotalAmount())
        );
        ArgumentCaptor<OrderCoupon> orderCouponCaptor =
                ArgumentCaptor.forClass(
                        OrderCoupon.class
                );

        verify(orderCouponRepository)
                .save(
                        orderCouponCaptor.capture()
                );

        OrderCoupon savedOrderCoupon =
                orderCouponCaptor.getValue();

        assertNotNull(
                savedOrderCoupon.getOrder()
        );

        assertEquals(
                "OMNIA10",
                savedOrderCoupon
                        .getCoupon()
                        .getCode()
        );

        assertEquals(
                0,
                new BigDecimal("3.59")
                        .compareTo(
                                savedOrderCoupon
                                        .getDiscountAmount()
                        )
        );
    }
    @Test
    void createOrder_shouldRemoveShippingWhenFreeCouponIsUsed() {

        Product product = Product.builder()
                .id(32L)
                .name("Free Shipping Product")
                .price(new BigDecimal("35.90"))
                .discountPrice(null)
                .stock(10)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingName("Test User")
                .shippingEmail("test@example.com")
                .shippingPhone("+355690000000")
                .shippingAddress("Tirane, Shqiperi")
                .couponCode("FREE")
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .productId(32L)
                                .quantity(1)
                                .build()
                ))
                .build();

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        when(productRepository.findByIdForUpdate(32L))
                .thenReturn(Optional.of(product));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(62L);
                    return savedOrder;
                });

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.createOrder(request);

        assertEquals("FREE", result.getCouponCode());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(result.getShippingFee())
        );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(result.getDiscountAmount())
        );

        assertEquals(
                0,
                new BigDecimal("35.90")
                        .compareTo(result.getTotalAmount())
        );
    }
    @Test
    void createOrder_shouldApplyWelcome5Coupon() {

        Product product = Product.builder()
                .id(33L)
                .name("Welcome Coupon Product")
                .price(new BigDecimal("35.90"))
                .discountPrice(null)
                .stock(10)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingName("Test User")
                .shippingEmail("test@example.com")
                .shippingPhone("+355690000000")
                .shippingAddress("Tirane, Shqiperi")
                .couponCode("WELCOME5")
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .productId(33L)
                                .quantity(1)
                                .build()
                ))
                .build();

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        when(productRepository.findByIdForUpdate(33L))
                .thenReturn(Optional.of(product));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(63L);
                    return savedOrder;
                });

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.createOrder(request);

        assertEquals("WELCOME5", result.getCouponCode());

        assertEquals(
                0,
                new BigDecimal("5.00")
                        .compareTo(result.getDiscountAmount())
        );

        assertEquals(
                0,
                new BigDecimal("34.40")
                        .compareTo(result.getTotalAmount())
        );
    }

    @Test
    void createOrder_shouldRejectInvalidCoupon() {

        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingName("Test User")
                .shippingEmail("test@example.com")
                .shippingPhone("+355690000000")
                .shippingAddress("Tirane, Shqiperi")
                .couponCode("INVALID50")
                .items(List.of(firstItemRequest))
                .build();

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(request)
        );

        assertEquals(
                "Invalid coupon code",
                exception.getMessage()
        );

        verify(orderRepository, never()).save(any(Order.class));
        verify(orderItemRepository, never()).saveAll(any());
        verify(paymentRepository, never()).save(any(Payment.class));
    }
    @Test
    void createOrder_shouldRejectInactiveCoupon() {

        Coupon inactiveCoupon =
                Coupon.builder()
                        .id(204L)
                        .code("INACTIVE10")
                        .discountType(
                                DiscountType.PERCENTAGE
                        )
                        .discountValue(
                                new BigDecimal("10.00")
                        )
                        .minimumOrderAmount(
                                BigDecimal.ZERO
                        )
                        .status(
                                CouponStatus.INACTIVE
                        )
                        .build();

        when(
                couponRepository.findByCodeForUpdate(
                        "INACTIVE10"
                )
        ).thenReturn(
                Optional.of(inactiveCoupon)
        );

        when(
                userRepository.findByEmail(
                        "shkelqim@example.com"
                )
        ).thenReturn(
                Optional.of(currentUser)
        );

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .shippingName("Test User")
                        .shippingEmail("test@example.com")
                        .shippingPhone("+355690000000")
                        .shippingAddress("Tirane, Shqiperi")
                        .couponCode("INACTIVE10")
                        .items(
                                List.of(firstItemRequest)
                        )
                        .build();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> orderService.createOrder(
                                request
                        )
                );

        assertEquals(
                "Coupon is inactive",
                exception.getMessage()
        );

        verify(orderRepository, never())
                .save(any(Order.class));
    }
    @Test
    void createOrder_shouldRejectExpiredCoupon() {

        Coupon expiredCoupon =
                Coupon.builder()
                        .id(205L)
                        .code("EXPIRED10")
                        .discountType(
                                DiscountType.PERCENTAGE
                        )
                        .discountValue(
                                new BigDecimal("10.00")
                        )
                        .minimumOrderAmount(
                                BigDecimal.ZERO
                        )
                        .endDate(
                                LocalDateTime.now()
                                        .minusDays(1)
                        )
                        .status(
                                CouponStatus.ACTIVE
                        )
                        .build();

        when(
                couponRepository.findByCodeForUpdate(
                        "EXPIRED10"
                )
        ).thenReturn(
                Optional.of(expiredCoupon)
        );

        when(
                userRepository.findByEmail(
                        "shkelqim@example.com"
                )
        ).thenReturn(
                Optional.of(currentUser)
        );

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .shippingName("Test User")
                        .shippingEmail("test@example.com")
                        .shippingPhone("+355690000000")
                        .shippingAddress("Tirane, Shqiperi")
                        .couponCode("EXPIRED10")
                        .items(
                                List.of(firstItemRequest)
                        )
                        .build();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> orderService.createOrder(
                                request
                        )
                );

        assertEquals(
                "Coupon has expired",
                exception.getMessage()
        );

        verify(orderRepository, never())
                .save(any(Order.class));
    }
    @Test
    void createOrder_shouldRejectCouponBelowMinimumOrderAmount() {

        Coupon minimumCoupon =
                Coupon.builder()
                        .id(206L)
                        .code("MINIMUM50")
                        .discountType(
                                DiscountType.FIXED
                        )
                        .discountValue(
                                new BigDecimal("5.00")
                        )
                        .minimumOrderAmount(
                                new BigDecimal("3000.00")
                        )
                        .status(
                                CouponStatus.ACTIVE
                        )
                        .build();

        when(
                couponRepository.findByCodeForUpdate(
                        "MINIMUM50"
                )
        ).thenReturn(
                Optional.of(minimumCoupon)
        );

        when(
                userRepository.findByEmail(
                        "shkelqim@example.com"
                )
        ).thenReturn(
                Optional.of(currentUser)
        );

        when(
                orderRepository.save(
                        any(Order.class)
                )
        ).thenReturn(initialSavedOrder);

        when(
                productRepository.findByIdForUpdate(
                        10L
                )
        ).thenReturn(
                Optional.of(discountedProduct)
        );

        when(
                orderItemRepository.saveAll(any())
        ).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .shippingName("Test User")
                        .shippingEmail("test@example.com")
                        .shippingPhone("+355690000000")
                        .shippingAddress("Tirane, Shqiperi")
                        .couponCode("MINIMUM50")
                        .items(
                                List.of(firstItemRequest)
                        )
                        .build();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> orderService.createOrder(
                                request
                        )
                );

        assertEquals(
                "Minimum order amount for coupon is not reached",
                exception.getMessage()
        );

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verify(orderCouponRepository, never())
                .save(any(OrderCoupon.class));
    }
    @Test
    void createOrder_shouldRejectCouponWhenUsageLimitIsReached() {

        Coupon limitedCoupon =
                Coupon.builder()
                        .id(207L)
                        .code("LIMITED10")
                        .discountType(
                                DiscountType.PERCENTAGE
                        )
                        .discountValue(
                                new BigDecimal("10.00")
                        )
                        .minimumOrderAmount(
                                BigDecimal.ZERO
                        )
                        .usageLimit(2)
                        .status(
                                CouponStatus.ACTIVE
                        )
                        .build();

        when(
                couponRepository.findByCodeForUpdate(
                        "LIMITED10"
                )
        ).thenReturn(
                Optional.of(limitedCoupon)
        );

        when(
                orderCouponRepository
                        .countUsagesExcludingStatus(
                                207L,
                                OrderStatus.CANCELLED
                        )
        ).thenReturn(2L);

        when(
                userRepository.findByEmail(
                        "shkelqim@example.com"
                )
        ).thenReturn(
                Optional.of(currentUser)
        );

        when(
                orderRepository.save(
                        any(Order.class)
                )
        ).thenReturn(initialSavedOrder);

        when(
                productRepository.findByIdForUpdate(
                        10L
                )
        ).thenReturn(
                Optional.of(discountedProduct)
        );

        when(
                orderItemRepository.saveAll(any())
        ).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .shippingName("Test User")
                        .shippingEmail("test@example.com")
                        .shippingPhone("+355690000000")
                        .shippingAddress("Tirane, Shqiperi")
                        .couponCode("LIMITED10")
                        .items(
                                List.of(firstItemRequest)
                        )
                        .build();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> orderService.createOrder(
                                request
                        )
                );

        assertEquals(
                "Coupon usage limit has been reached",
                exception.getMessage()
        );

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verify(orderCouponRepository, never())
                .save(any(OrderCoupon.class));
    }
    @Test
    void createOrder_shouldRejectCouponWhenUserLimitIsReached() {

        when(
                orderCouponRepository
                        .countUserUsagesExcludingStatus(
                                202L,
                                currentUser.getId(),
                                OrderStatus.CANCELLED
                        )
        ).thenReturn(1L);

        when(
                userRepository.findByEmail(
                        "shkelqim@example.com"
                )
        ).thenReturn(
                Optional.of(currentUser)
        );

        when(
                orderRepository.save(
                        any(Order.class)
                )
        ).thenReturn(initialSavedOrder);

        when(
                productRepository.findByIdForUpdate(
                        10L
                )
        ).thenReturn(
                Optional.of(discountedProduct)
        );

        when(
                orderItemRepository.saveAll(any())
        ).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .shippingName("Test User")
                        .shippingEmail("test@example.com")
                        .shippingPhone("+355690000000")
                        .shippingAddress("Tirane, Shqiperi")
                        .couponCode("WELCOME5")
                        .items(
                                List.of(firstItemRequest)
                        )
                        .build();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> orderService.createOrder(
                                request
                        )
                );

        assertEquals(
                "Coupon usage limit for this user has been reached",
                exception.getMessage()
        );

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verify(orderCouponRepository, never())
                .save(any(OrderCoupon.class));
    }
    @Test
    void createOrder_shouldDecreaseProductStock() {

        Product product = Product.builder()
                .id(40L)
                .name("Stock Test Product")
                .price(new BigDecimal("10.00"))
                .stock(7)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingName("Test User")
                .shippingEmail("test@example.com")
                .shippingPhone("+355690000000")
                .shippingAddress("Tirane, Shqiperi")
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .productId(40L)
                                .quantity(3)
                                .build()
                ))
                .build();

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        when(productRepository.findByIdForUpdate(40L))
                .thenReturn(Optional.of(product));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(70L);
                    return savedOrder;
                });

        when(orderItemRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        orderService.createOrder(request);

        assertEquals(4, product.getStock());
        verify(productRepository).findByIdForUpdate(40L);
    }
    @Test
    void createOrder_shouldRejectOrderWhenStockIsInsufficient() {

        Product product = Product.builder()
                .id(41L)
                .name("Limited Stock Product")
                .price(new BigDecimal("10.00"))
                .stock(2)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingName("Test User")
                .shippingEmail("test@example.com")
                .shippingPhone("+355690000000")
                .shippingAddress("Tirane, Shqiperi")
                .items(List.of(
                        CreateOrderItemRequest.builder()
                                .productId(41L)
                                .quantity(3)
                                .build()
                ))
                .build();

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        when(productRepository.findByIdForUpdate(41L))
                .thenReturn(Optional.of(product));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(request)
        );

        assertEquals(
                "Insufficient stock for product: Limited Stock Product",
                exception.getMessage()
        );

        assertEquals(2, product.getStock());
        verify(orderItemRepository, never()).saveAll(any());
        verify(paymentRepository, never()).save(any(Payment.class));
    }
    @Test
    void cancelMyOrder_shouldCancelOrderAndRestoreStock() {

        Order order = Order.builder()
                .id(70L)
                .user(currentUser)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("2000.00"))
                .build();

        OrderItem item = OrderItem.builder()
                .id(80L)
                .order(order)
                .productId(discountedProduct.getId())
                .productName(discountedProduct.getName())
                .unitPrice(new BigDecimal("1000.00"))
                .quantity(2)
                .subtotal(new BigDecimal("2000.00"))
                .build();

        discountedProduct.setStock(5);

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.findById(70L))
                .thenReturn(Optional.of(order));

        when(orderItemRepository.findByOrderId(70L))
                .thenReturn(List.of(item));

        when(productRepository.findByIdForUpdate(
                discountedProduct.getId()
        )).thenReturn(Optional.of(discountedProduct));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        OrderResponse result =
                orderService.cancelMyOrder(70L);

        assertEquals(
                OrderStatus.CANCELLED,
                result.getStatus()
        );

        assertEquals(
                7,
                discountedProduct.getStock()
        );

        verify(productRepository)
                .findByIdForUpdate(
                        discountedProduct.getId()
                );

        verify(orderRepository)
                .save(order);
        ArgumentCaptor<OrderStatusChangedEvent>
                cancellationEventCaptor =
                ArgumentCaptor.forClass(
                        OrderStatusChangedEvent.class
                );

        verify(eventPublisher)
                .publishEvent(
                        cancellationEventCaptor.capture()
                );

        OrderStatusChangedEvent cancellationEvent =
                cancellationEventCaptor.getValue();

        assertEquals(
                70L,
                cancellationEvent.orderId()
        );
        assertEquals(
                OrderStatus.CANCELLED,
                cancellationEvent.status()
        );
        assertEquals(
                "shkelqim@example.com",
                cancellationEvent.recipientEmail()
        );
        assertEquals(
                0,
                new BigDecimal("2000.00")
                        .compareTo(
                                cancellationEvent.totalAmount()
                        )
        );
    }
    @Test
    void cancelMyOrder_shouldRejectShippedOrder() {

        Order shippedOrder = Order.builder()
                .id(71L)
                .user(currentUser)
                .status(OrderStatus.SHIPPED)
                .totalAmount(new BigDecimal("39.40"))
                .build();

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(currentUser));

        when(orderRepository.findById(71L))
                .thenReturn(Optional.of(shippedOrder));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> orderService.cancelMyOrder(
                                71L
                        )
                );

        assertEquals(
                "Shipped or delivered orders cannot be cancelled",
                exception.getMessage()
        );

        verifyNoInteractions(
                orderItemRepository
        );

        verify(productRepository, never())
                .findByIdForUpdate(anyLong());

        verify(orderRepository, never())
                .save(any(Order.class));
    }
    @Test
    void updateOrderStatusForAdmin_shouldConfirmPendingOrder() {

        Order order = Order.builder()
                .id(90L)
                .user(currentUser)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("39.40"))
                .build();

        when(orderRepository.findById(90L))
                .thenReturn(Optional.of(order));

        when(orderItemRepository.findByOrderId(90L))
                .thenReturn(List.of());

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        OrderResponse result =
                orderService.updateOrderStatusForAdmin(
                        90L,
                        OrderStatus.CONFIRMED
                );

        assertEquals(
                OrderStatus.CONFIRMED,
                result.getStatus()
        );

        verify(orderRepository)
                .save(order);
        ArgumentCaptor<OrderStatusHistory> historyCaptor =
                ArgumentCaptor.forClass(
                        OrderStatusHistory.class
                );

        verify(orderStatusHistoryRepository)
                .save(historyCaptor.capture());

        OrderStatusHistory savedHistory =
                historyCaptor.getValue();

        assertEquals(
                90L,
                savedHistory.getOrder().getId()
        );
        assertEquals(
                OrderStatus.PENDING,
                savedHistory.getFromStatus()
        );
        assertEquals(
                OrderStatus.CONFIRMED,
                savedHistory.getToStatus()
        );
        assertEquals(
                currentUser,
                savedHistory.getChangedByUser()
        );
        ArgumentCaptor<OrderStatusChangedEvent>
                notificationCaptor =
                ArgumentCaptor.forClass(
                        OrderStatusChangedEvent.class
                );

        verify(eventPublisher)
                .publishEvent(
                        notificationCaptor.capture()
                );

        OrderStatusChangedEvent notification =
                notificationCaptor.getValue();

        assertEquals(90L, notification.orderId());
        assertEquals(
                OrderStatus.CONFIRMED,
                notification.status()
        );
        assertEquals(
                "shkelqim@example.com",
                notification.recipientEmail()
        );
        assertEquals(
                0,
                new BigDecimal("39.40")
                        .compareTo(
                                notification.totalAmount()
                        )
        );

        verify(productRepository, never())
                .findByIdForUpdate(anyLong());
    }
    @Test
    void updateOrderStatusForAdmin_shouldMarkCodPaymentAsSuccessfulWhenDelivered() {

        Order order = Order.builder()
                .id(94L)
                .user(currentUser)
                .status(OrderStatus.SHIPPED)
                .totalAmount(new BigDecimal("39.40"))
                .build();

        Payment payment = Payment.builder()
                .id(95L)
                .order(order)
                .method(PaymentMethod.CASH_ON_DELIVERY)
                .status(PaymentStatus.PENDING)
                .build();

        when(orderRepository.findById(94L))
                .thenReturn(Optional.of(order));

        when(orderItemRepository.findByOrderId(94L))
                .thenReturn(List.of());

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(paymentRepository.findByOrderId(94L))
                .thenReturn(Optional.of(payment));

        OrderResponse result =
                orderService.updateOrderStatusForAdmin(
                        94L,
                        OrderStatus.DELIVERED
                );

        assertEquals(
                OrderStatus.DELIVERED,
                result.getStatus()
        );

        assertEquals(
                PaymentStatus.SUCCESS,
                payment.getStatus()
        );

        assertNotNull(payment.getPaidAt());

        assertEquals(
                PaymentMethod.CASH_ON_DELIVERY,
                result.getPaymentMethod()
        );
        assertEquals(
                PaymentStatus.SUCCESS,
                result.getPaymentStatus()
        );
        assertEquals(
                payment.getPaidAt(),
                result.getPaidAt()
        );
        assertNull(result.getTransactionId());


        verify(paymentRepository)
                .save(payment);
    }

    @Test
    void updateOrderStatusForAdmin_shouldCancelAndRestoreStock() {

        Order order = Order.builder()
                .id(91L)
                .user(currentUser)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("39.40"))
                .build();

        OrderItem item = OrderItem.builder()
                .id(92L)
                .order(order)
                .productId(discountedProduct.getId())
                .productName(discountedProduct.getName())
                .unitPrice(new BigDecimal("35.90"))
                .quantity(1)
                .subtotal(new BigDecimal("35.90"))
                .build();

        discountedProduct.setStock(4);

        when(orderRepository.findById(91L))
                .thenReturn(Optional.of(order));

        when(orderItemRepository.findByOrderId(91L))
                .thenReturn(List.of(item));

        when(productRepository.findByIdForUpdate(
                discountedProduct.getId()
        )).thenReturn(Optional.of(discountedProduct));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        OrderResponse result =
                orderService.updateOrderStatusForAdmin(
                        91L,
                        OrderStatus.CANCELLED
                );

        assertEquals(
                OrderStatus.CANCELLED,
                result.getStatus()
        );

        assertEquals(
                5,
                discountedProduct.getStock()
        );

        verify(orderRepository)
                .save(order);
    }

    @Test
    void updateOrderStatusForAdmin_shouldRejectInvalidTransition() {

        Order order = Order.builder()
                .id(93L)
                .user(currentUser)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("39.40"))
                .build();

        when(orderRepository.findById(93L))
                .thenReturn(Optional.of(order));

        when(orderItemRepository.findByOrderId(93L))
                .thenReturn(List.of());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> orderService
                                .updateOrderStatusForAdmin(
                                        93L,
                                        OrderStatus.DELIVERED
                                )
                );

        assertTrue(
                exception.getMessage().contains(
                        "Invalid order status transition"
                )
        );

        verify(orderRepository, never())
                .save(any(Order.class));

        verify(productRepository, never())
                .findByIdForUpdate(anyLong());
    }
    @Test
    void getOrderStatusHistoryForAdmin_shouldReturnMappedHistory() {

        Order order = Order.builder()
                .id(90L)
                .user(currentUser)
                .status(OrderStatus.CONFIRMED)
                .build();

        LocalDateTime changedAt =
                LocalDateTime.of(
                        2026,
                        8,
                        23,
                        12,
                        0
                );

        OrderStatusHistory history =
                OrderStatusHistory.builder()
                        .id(100L)
                        .order(order)
                        .fromStatus(OrderStatus.PENDING)
                        .toStatus(OrderStatus.CONFIRMED)
                        .changedByUser(currentUser)
                        .changedAt(changedAt)
                        .build();

        when(orderRepository.existsById(90L))
                .thenReturn(true);

        when(orderStatusHistoryRepository.findAllForOrder(90L))
                .thenReturn(List.of(history));

        List<OrderStatusHistoryResponse> result =
                orderService.getOrderStatusHistoryForAdmin(90L);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getId());
        assertEquals(90L, result.get(0).getOrderId());
        assertEquals(
                OrderStatus.PENDING,
                result.get(0).getFromStatus()
        );
        assertEquals(
                OrderStatus.CONFIRMED,
                result.get(0).getToStatus()
        );
        assertEquals(1L, result.get(0).getChangedByUserId());
        assertEquals(
                "shkelqim",
                result.get(0).getChangedByName()
        );
        assertEquals(
                changedAt,
                result.get(0).getChangedAt()
        );
    }
    @Test
    void getOrderStatusHistoryForAdmin_shouldRejectMissingOrder() {

        when(orderRepository.existsById(999L))
                .thenReturn(false);

        assertThrows(
                ResourceNotFoundException.class,
                () -> orderService
                        .getOrderStatusHistoryForAdmin(999L)
        );

        verify(
                orderStatusHistoryRepository,
                never()
        ).findAllForOrder(anyLong());
    }
}