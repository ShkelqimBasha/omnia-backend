package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.CreateOrderItemRequest;
import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.dto.response.OrderResponse;
import com.omnia.backend.entity.Order;
import com.omnia.backend.entity.OrderItem;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.User;
import com.omnia.backend.entity.Payment;
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.enums.PaymentStatus;
import com.omnia.backend.mapper.OrderMapper;
import com.omnia.backend.repository.OrderItemRepository;
import com.omnia.backend.repository.OrderRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.repository.PaymentRepository;
import com.omnia.backend.service.interfaces.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.Locale;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
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

        if (couponCode != null
                && !couponCode.equals("OMNIA10")
                && !couponCode.equals("WELCOME5")
                && !couponCode.equals("FREE")) {
            throw new IllegalArgumentException("Invalid coupon code");
        }

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

        BigDecimal discountAmount = BigDecimal.ZERO;

        if ("OMNIA10".equals(couponCode)) {
            discountAmount = subtotalAmount
                    .multiply(new BigDecimal("0.10"))
                    .setScale(2, RoundingMode.HALF_UP);
        } else if ("WELCOME5".equals(couponCode)) {
            discountAmount = subtotalAmount.min(
                    new BigDecimal("5.00")
            );
        }

        BigDecimal shippingFee =
                subtotalAmount.signum() == 0
                        || subtotalAmount.compareTo(
                        new BigDecimal("50.00")
                ) > 0
                        || "FREE".equals(couponCode)
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

        paymentRepository.save(payment);

        return OrderMapper.toResponse(finalOrder, orderItems);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders() {

        User user = getCurrentUser();

        return orderRepository.findByUserId(user.getId())
                .stream()
                .map(order -> {
                    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
                    return OrderMapper.toResponse(order, items);
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

        return OrderMapper.toResponse(order, items);
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