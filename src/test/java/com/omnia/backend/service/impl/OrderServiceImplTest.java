package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.CreateOrderItemRequest;
import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.dto.response.OrderResponse;
import com.omnia.backend.entity.Order;
import com.omnia.backend.entity.OrderItem;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.repository.OrderItemRepository;
import com.omnia.backend.repository.OrderRepository;
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
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

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
                .build();

        regularProduct = Product.builder()
                .id(20L)
                .name("Laptop Lenovo")
                .price(new BigDecimal("800.00"))
                .discountPrice(null)
                .build();

        firstItemRequest = CreateOrderItemRequest.builder()
                .productId(10L)
                .quantity(2)
                .build();

        createOrderRequest = CreateOrderRequest.builder()
                .addressId(100L)
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

        when(productRepository.findById(10L))
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

        verify(productRepository).findById(10L);
        verify(orderItemRepository).saveAll(any());
        verify(orderRepository, times(2))
                .save(any(Order.class));
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

        when(productRepository.findById(20L))
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

        when(productRepository.findById(10L))
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

        when(productRepository.findById(10L))
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

        when(productRepository.findById(10L))
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

        when(productRepository.findById(99L))
                .thenReturn(Optional.empty());

        CreateOrderItemRequest missingProductItem =
                CreateOrderItemRequest.builder()
                        .productId(99L)
                        .quantity(1)
                        .build();

        CreateOrderRequest request =
                CreateOrderRequest.builder()
                        .addressId(100L)
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

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(discountedProduct));

        when(productRepository.findById(20L))
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

        verify(productRepository).findById(10L);
        verify(productRepository).findById(20L);
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

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(discountedProduct));

        when(productRepository.findById(20L))
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

        when(productRepository.findById(10L))
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

        when(productRepository.findById(10L))
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

        when(productRepository.findById(10L))
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
}