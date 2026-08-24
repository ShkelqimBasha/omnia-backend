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
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.enums.PaymentStatus;
import com.omnia.backend.mapper.OrderMapper;
import com.omnia.backend.repository.OrderItemRepository;
import com.omnia.backend.repository.OrderRepository;
import com.omnia.backend.repository.OrderStatusHistoryRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.repository.PaymentRepository;
import com.omnia.backend.service.interfaces.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.omnia.backend.event.OrderStatusChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.omnia.backend.entity.Coupon;
import com.omnia.backend.entity.OrderCoupon;
import com.omnia.backend.enums.CouponStatus;
import com.omnia.backend.enums.DiscountType;
import com.omnia.backend.repository.CouponRepository;
import com.omnia.backend.repository.OrderCouponRepository;

import java.time.LocalDateTime;

import java.math.RoundingMode;
import java.util.Locale;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository
            orderStatusHistoryRepository;
    private final CouponRepository couponRepository;
    private final OrderCouponRepository
            orderCouponRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderStatusHistoryRepository
                    orderStatusHistoryRepository,
            CouponRepository couponRepository,
            OrderCouponRepository
                    orderCouponRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository =
                orderStatusHistoryRepository;
        this.couponRepository = couponRepository;
        this.orderCouponRepository =
                orderCouponRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {

        User user = getCurrentUser();

        String couponCode = request.getCouponCode() == null
                ? null
                : request.getCouponCode()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (couponCode != null && couponCode.isEmpty()) {
            couponCode = null;
        }
        Coupon appliedCoupon =
                findCouponForCheckout(
                        couponCode
                );



        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        Order order = Order.builder()
                .user(user)
                .addressId(request.getAddressId())
                .shippingName(request.getShippingName().trim())
                .shippingEmail(
                        request.getShippingEmail()
                                .trim()
                                .toLowerCase(java.util.Locale.ROOT)
                )
                .shippingPhone(request.getShippingPhone().trim())
                .shippingAddress(request.getShippingAddress().trim())
                .totalAmount(BigDecimal.ZERO)
                .subtotalAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .couponCode(couponCode)
                .status(OrderStatus.PENDING)
                .build();


        Order savedOrder = orderRepository.save(order);
        recordOrderStatusChange(
                savedOrder,
                null,
                OrderStatus.PENDING,
                user
        );


        for (CreateOrderItemRequest itemRequest : request.getItems()) {

            Product product = productRepository
                    .findByIdForUpdate(itemRequest.getProductId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Product not found"
                            )
                    );

            int requestedQuantity = itemRequest.getQuantity();
            int availableStock = product.getStock() == null
                    ? 0
                    : product.getStock();

            if (requestedQuantity > availableStock) {
                throw new IllegalArgumentException(
                        "Insufficient stock for product: "
                                + product.getName()
                );
            }

            BigDecimal unitPrice = product.getDiscountPrice() != null
                    ? product.getDiscountPrice()
                    : product.getPrice();

            BigDecimal subtotal = unitPrice.multiply(
                    BigDecimal.valueOf(itemRequest.getQuantity())
            );

            OrderItem orderItem = OrderItem.builder()
                    .order(savedOrder)
                    .productId(product.getId())
                    .productName(product.getName())
                    .productImage(null)
                    .variantInfo(null)
                    .unitPrice(unitPrice)
                    .quantity(itemRequest.getQuantity())
                    .subtotal(subtotal)
                    .build();

            totalAmount = totalAmount.add(subtotal);
            orderItems.add(orderItem);
            product.setStock(
                    availableStock - requestedQuantity
            );
        }

        orderItemRepository.saveAll(orderItems);

        BigDecimal subtotalAmount =
                totalAmount.setScale(2, RoundingMode.HALF_UP);

        validateCouponForCheckout(
                appliedCoupon,
                user,
                subtotalAmount
        );

        BigDecimal discountAmount =
                calculateCouponDiscount(
                        appliedCoupon,
                        subtotalAmount
                );

        boolean freeShippingCoupon =
                appliedCoupon != null
                        && appliedCoupon
                        .getDiscountType()
                        == DiscountType.FREE_SHIPPING;

        BigDecimal shippingFee =
                subtotalAmount.signum() == 0
                        || subtotalAmount.compareTo(
                        new BigDecimal("50.00")
                ) > 0
                        || freeShippingCoupon
                        ? BigDecimal.ZERO
                        : new BigDecimal("3.50");

        BigDecimal finalTotal = subtotalAmount
                .add(shippingFee)
                .subtract(discountAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        savedOrder.setSubtotalAmount(subtotalAmount);
        savedOrder.setShippingFee(shippingFee);
        savedOrder.setDiscountAmount(discountAmount);
        savedOrder.setCouponCode(couponCode);
        savedOrder.setTotalAmount(finalTotal);

        Order finalOrder = orderRepository.save(savedOrder);

        Payment payment = Payment.builder()
                .order(finalOrder)
                .method(PaymentMethod.CASH_ON_DELIVERY)
                .status(PaymentStatus.PENDING)
                .build();
        if (appliedCoupon != null) {
            OrderCoupon orderCoupon =
                    OrderCoupon.builder()
                            .order(finalOrder)
                            .coupon(appliedCoupon)
                            .discountAmount(
                                    discountAmount
                            )
                            .build();

            orderCouponRepository.save(
                    orderCoupon
            );
        }

        paymentRepository.save(payment);
        publishOrderStatusChangedEvent(
                finalOrder
        );

        return OrderMapper.toResponse(
                finalOrder,
                orderItems,
                payment
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders() {

        User user = getCurrentUser();

        return orderRepository.findByUserId(user.getId())
                .stream()
                .map(order -> {
                    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
                    return mapOrderResponse(order, items);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {

        User user = getCurrentUser();

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You are not allowed to access this order");
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

        return mapOrderResponse(order, items);
    }
    @Override
    @Transactional
    public OrderResponse cancelMyOrder(Long id) {

        User user = getCurrentUser();

        Order order = orderRepository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Order not found"
                        )
                );

        if (!order.getUser()
                .getId()
                .equals(user.getId())) {
            throw new RuntimeException(
                    "You are not allowed to cancel this order"
            );
        }

        OrderStatus currentStatus =
                order.getStatus();

        if (currentStatus == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "Order is already cancelled"
            );
        }

        if (currentStatus == OrderStatus.SHIPPED
                || currentStatus == OrderStatus.DELIVERED) {
            throw new IllegalArgumentException(
                    "Shipped or delivered orders cannot be cancelled"
            );
        }

        List<OrderItem> items =
                orderItemRepository.findByOrderId(
                        order.getId()
                );

        for (OrderItem item : items) {

            Product product =
                    productRepository.findByIdForUpdate(
                                    item.getProductId()
                            )
                            .orElseThrow(
                                    () -> new ResourceNotFoundException(
                                            "Product not found"
                                    )
                            );

            int currentStock =
                    product.getStock() == null
                            ? 0
                            : product.getStock();

            product.setStock(
                    currentStock + item.getQuantity()
            );
        }

        order.setStatus(OrderStatus.CANCELLED);

        Order cancelledOrder =
                orderRepository.save(order);
        recordOrderStatusChange(
                cancelledOrder,
                currentStatus,
                OrderStatus.CANCELLED,
                user
        );
        publishOrderStatusChangedEvent(
                cancelledOrder
        );



        return mapOrderResponse(
                cancelledOrder,
                items
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrdersForAdmin() {

        return orderRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(order -> {
                    List<OrderItem> items =
                            orderItemRepository.findByOrderId(
                                    order.getId()
                            );

                    return mapOrderResponse(
                            order,
                            items
                    );
                })
                .toList();
    }
    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse>
    getOrderStatusHistoryForAdmin(
            Long id
    ) {
        if (!orderRepository.existsById(id)) {
            throw new ResourceNotFoundException(
                    "Order not found"
            );
        }

        return orderStatusHistoryRepository
                .findAllForOrder(id)
                .stream()
                .map(history -> {
                    User changedByUser =
                            history.getChangedByUser();

                    return OrderStatusHistoryResponse
                            .builder()
                            .id(history.getId())
                            .orderId(id)
                            .fromStatus(
                                    history.getFromStatus()
                            )
                            .toStatus(
                                    history.getToStatus()
                            )
                            .changedByUserId(
                                    changedByUser == null
                                            ? null
                                            : changedByUser.getId()
                            )
                            .changedByName(
                                    getChangedByDisplayName(
                                            changedByUser
                                    )
                            )
                            .changedAt(
                                    history.getChangedAt()
                            )
                            .build();
                })
                .toList();
    }

    private String getChangedByDisplayName(
            User user
    ) {
        if (user == null) {
            return "System";
        }

        String firstName =
                user.getFirstName() == null
                        ? ""
                        : user.getFirstName().trim();

        String lastName =
                user.getLastName() == null
                        ? ""
                        : user.getLastName().trim();

        String fullName =
                (firstName + " " + lastName)
                        .trim();

        if (!fullName.isEmpty()) {
            return fullName;
        }

        if (user.getUsername() != null
                && !user.getUsername()
                .trim()
                .isEmpty()) {
            return user.getUsername().trim();
        }

        return user.getEmail();
    }


    @Override
    @Transactional
    public OrderResponse updateOrderStatusForAdmin(
            Long id,
            OrderStatus newStatus
    ) {
        if (newStatus == null) {
            throw new IllegalArgumentException(
                    "Order status is required"
            );
        }

        Order order = orderRepository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Order not found"
                        )
                );

        OrderStatus currentStatus =
                order.getStatus();

        List<OrderItem> items =
                orderItemRepository.findByOrderId(
                        order.getId()
                );

        if (currentStatus == newStatus) {
            return mapOrderResponse(
                    order,
                    items
            );
        }

        if (!isAllowedAdminStatusTransition(
                currentStatus,
                newStatus
        )) {
            throw new IllegalArgumentException(
                    "Invalid order status transition: "
                            + currentStatus
                            + " -> "
                            + newStatus
            );
        }

        if (newStatus == OrderStatus.CANCELLED) {
            restoreOrderStock(items);
        }

        order.setStatus(newStatus);

        Order savedOrder =
                orderRepository.save(order);
        if (newStatus == OrderStatus.DELIVERED) {
            markCashOnDeliveryAsPaid(savedOrder);
        }
        recordOrderStatusChange(
                savedOrder,
                currentStatus,
                newStatus,
                getCurrentUser()
        );
        publishOrderStatusChangedEvent(
                savedOrder
        );


        return mapOrderResponse(
                savedOrder,
                items
        );
    }
    private Coupon findCouponForCheckout(
            String couponCode
    ) {
        if (couponCode == null) {
            return null;
        }

        Coupon coupon =
                couponRepository.findByCodeForUpdate(
                                couponCode
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Invalid coupon code"
                                )
                        );

        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Coupon is inactive"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (coupon.getStartDate() != null
                && now.isBefore(coupon.getStartDate())) {
            throw new IllegalArgumentException(
                    "Coupon is not active yet"
            );
        }

        if (coupon.getEndDate() != null
                && now.isAfter(coupon.getEndDate())) {
            throw new IllegalArgumentException(
                    "Coupon has expired"
            );
        }

        return coupon;
    }

    private void validateCouponForCheckout(
            Coupon coupon,
            User user,
            BigDecimal subtotalAmount
    ) {
        if (coupon == null) {
            return;
        }

        BigDecimal minimumOrderAmount =
                coupon.getMinimumOrderAmount() == null
                        ? BigDecimal.ZERO
                        : coupon.getMinimumOrderAmount();

        if (subtotalAmount.compareTo(
                minimumOrderAmount
        ) < 0) {
            throw new IllegalArgumentException(
                    "Minimum order amount for coupon is not reached"
            );
        }

        if (coupon.getUsageLimit() != null) {
            long totalUsages =
                    orderCouponRepository
                            .countUsagesExcludingStatus(
                                    coupon.getId(),
                                    OrderStatus.CANCELLED
                            );

            if (totalUsages
                    >= coupon.getUsageLimit()) {
                throw new IllegalArgumentException(
                        "Coupon usage limit has been reached"
                );
            }
        }

        if (coupon.getPerUserLimit() != null) {
            long userUsages =
                    orderCouponRepository
                            .countUserUsagesExcludingStatus(
                                    coupon.getId(),
                                    user.getId(),
                                    OrderStatus.CANCELLED
                            );

            if (userUsages
                    >= coupon.getPerUserLimit()) {
                throw new IllegalArgumentException(
                        "Coupon usage limit for this user has been reached"
                );
            }
        }
    }
    private BigDecimal calculateCouponDiscount(
            Coupon coupon,
            BigDecimal subtotalAmount
    ) {
        if (coupon == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal discountAmount =
                switch (
                        coupon.getDiscountType()
                        ) {
                    case PERCENTAGE ->
                            subtotalAmount
                                    .multiply(
                                            coupon.getDiscountValue()
                                    )
                                    .divide(
                                            new BigDecimal("100"),
                                            2,
                                            RoundingMode.HALF_UP
                                    );

                    case FIXED ->
                            coupon.getDiscountValue();

                    case FREE_SHIPPING ->
                            BigDecimal.ZERO;
                };

        return discountAmount
                .max(BigDecimal.ZERO)
                .min(subtotalAmount)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }
    private void recordOrderStatusChange(
            Order order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            User changedByUser
    ) {
        OrderStatusHistory history =
                OrderStatusHistory.builder()
                        .order(order)
                        .fromStatus(fromStatus)
                        .toStatus(toStatus)
                        .changedByUser(changedByUser)
                        .build();

        orderStatusHistoryRepository.save(
                history
        );
    }
    private void publishOrderStatusChangedEvent(
            Order order
    ) {
        if (order == null) {
            return;
        }

        String recipientEmail =
                order.getShippingEmail();

        if ((recipientEmail == null
                || recipientEmail.trim().isEmpty())
                && order.getUser() != null) {
            recipientEmail =
                    order.getUser().getEmail();
        }

        if (recipientEmail == null
                || recipientEmail.trim().isEmpty()) {
            return;
        }

        String recipientName =
                order.getShippingName();

        if ((recipientName == null
                || recipientName.trim().isEmpty())
                && order.getUser() != null) {
            recipientName =
                    order.getUser().getUsername();
        }

        eventPublisher.publishEvent(
                new OrderStatusChangedEvent(
                        recipientEmail.trim(),
                        recipientName,
                        order.getId(),
                        order.getStatus(),
                        order.getTotalAmount()
                )
        );
    }


    private boolean isAllowedAdminStatusTransition(
            OrderStatus currentStatus,
            OrderStatus newStatus
    ) {
        if (currentStatus == null) {
            return newStatus == OrderStatus.PENDING;
        }

        switch (currentStatus) {
            case PENDING:
                return newStatus == OrderStatus.CONFIRMED
                        || newStatus == OrderStatus.CANCELLED;

            case CONFIRMED:
                return newStatus == OrderStatus.PROCESSING
                        || newStatus == OrderStatus.CANCELLED;

            case PROCESSING:
                return newStatus == OrderStatus.SHIPPED
                        || newStatus == OrderStatus.CANCELLED;

            case SHIPPED:
                return newStatus == OrderStatus.DELIVERED;

            case DELIVERED:
            case CANCELLED:
            default:
                return false;
        }
    }
    private OrderResponse mapOrderResponse(
            Order order,
            List<OrderItem> items
    ) {
        Payment payment =
                paymentRepository.findByOrderId(
                                order.getId()
                        )
                        .orElse(null);

        return OrderMapper.toResponse(
                order,
                items,
                payment
        );
    }

    private void markCashOnDeliveryAsPaid(
            Order order
    ) {
        Payment payment =
                paymentRepository.findByOrderId(
                                order.getId()
                        )
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Payment not found for order"
                                )
                        );

        if (payment.getMethod()
                != PaymentMethod.CASH_ON_DELIVERY) {
            return;
        }

        if (payment.getStatus()
                == PaymentStatus.SUCCESS) {
            return;
        }

        if (payment.getStatus()
                != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Cash on delivery payment is not pending"
            );
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(
                java.time.LocalDateTime.now()
        );
        paymentRepository.save(payment);

    }

    private void restoreOrderStock(
            List<OrderItem> items
    ) {
        for (OrderItem item : items) {

            Product product =
                    productRepository.findByIdForUpdate(
                                    item.getProductId()
                            )
                            .orElseThrow(
                                    () -> new ResourceNotFoundException(
                                            "Product not found"
                                    )
                            );

            int currentStock =
                    product.getStock() == null
                            ? 0
                            : product.getStock();

            product.setStock(
                    currentStock + item.getQuantity()
            );
        }
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