package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.CreateOrderItemRequest;
import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.dto.response.CheckoutResponse;
import com.omnia.backend.dto.response.OrderResponse;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.CouponStatus;
import com.omnia.backend.enums.DiscountType;
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.enums.PaymentStatus;
import com.omnia.backend.event.OrderStatusChangedEvent;
import com.omnia.backend.mapper.OrderMapper;
import com.omnia.backend.repository.*;
import com.omnia.backend.service.interfaces.CheckoutService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CheckoutServiceImpl
        implements CheckoutService {

    private static final BigDecimal
            FREE_SHIPPING_THRESHOLD =
            new BigDecimal("50.00");

    private static final BigDecimal
            STANDARD_SHIPPING_FEE =
            new BigDecimal("3.50");

    private static final BigDecimal ONE_CENT =
            new BigDecimal("0.01");

    private final CheckoutGroupRepository
            checkoutGroupRepository;

    private final CheckoutGroupCouponRepository
            checkoutGroupCouponRepository;

    private final OrderRepository orderRepository;

    private final OrderCouponRepository
            orderCouponRepository;

    private final OrderItemRepository
            orderItemRepository;

    private final OrderStatusHistoryRepository
            orderStatusHistoryRepository;

    private final ProductRepository productRepository;

    private final UserRepository userRepository;

    private final CouponRepository couponRepository;

    private final PaymentRepository paymentRepository;

    private final ApplicationEventPublisher
            eventPublisher;

    public CheckoutServiceImpl(
            CheckoutGroupRepository
                    checkoutGroupRepository,
            CheckoutGroupCouponRepository
                    checkoutGroupCouponRepository,
            OrderRepository orderRepository,
            OrderCouponRepository
                    orderCouponRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository
                    orderStatusHistoryRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            CouponRepository couponRepository,
            PaymentRepository paymentRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.checkoutGroupRepository =
                checkoutGroupRepository;
        this.checkoutGroupCouponRepository =
                checkoutGroupCouponRepository;
        this.orderRepository = orderRepository;
        this.orderCouponRepository =
                orderCouponRepository;
        this.orderItemRepository =
                orderItemRepository;
        this.orderStatusHistoryRepository =
                orderStatusHistoryRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.couponRepository = couponRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public CheckoutResponse checkout(
            CreateOrderRequest request
    ) {
        User user = getCurrentUser();

        String couponCode =
                normalizeCouponCode(
                        request.getCouponCode()
                );

        Coupon coupon =
                findCouponForCheckout(couponCode);

        Map<Long, Integer> requestedQuantities =
                mergeRequestedQuantities(
                        request.getItems()
                );

        List<Long> productIds =
                new ArrayList<>(
                        requestedQuantities.keySet()
                );

        productIds.sort(Long::compareTo);

        Map<Long, VendorOrderDraft> drafts =
                new LinkedHashMap<>();

        for (Long productId : productIds) {
            Product product = productRepository
                    .findByIdForUpdate(productId)
                    .orElseThrow(
                            () -> new ResourceNotFoundException(
                                    "Product not found"
                            )
                    );

            Organization organization =
                    product.getOrganization();

            if (organization == null) {
                throw new IllegalArgumentException(
                        "Product is not assigned "
                                + "to an organization: "
                                + product.getName()
                );
            }

            if (!organization.isActive()) {
                throw new IllegalArgumentException(
                        "Product organization is not active: "
                                + organization.getName()
                );
            }

            int requestedQuantity =
                    requestedQuantities.get(productId);

            int availableStock =
                    product.getStock() == null
                            ? 0
                            : product.getStock();

            if (requestedQuantity > availableStock) {
                throw new IllegalArgumentException(
                        "Insufficient stock for product: "
                                + product.getName()
                );
            }

            BigDecimal unitPrice =
                    product.getDiscountPrice() != null
                            ? product.getDiscountPrice()
                            : product.getPrice();

            BigDecimal itemSubtotal =
                    unitPrice.multiply(
                            BigDecimal.valueOf(
                                    requestedQuantity
                            )
                    ).setScale(
                            2,
                            RoundingMode.HALF_UP
                    );

            VendorOrderDraft draft =
                    drafts.computeIfAbsent(
                            organization.getId(),
                            ignored ->
                                    new VendorOrderDraft(
                                            organization
                                    )
                    );

            draft.items.add(
                    new PreparedItem(
                            product,
                            requestedQuantity,
                            unitPrice,
                            itemSubtotal
                    )
            );

            draft.subtotal =
                    draft.subtotal.add(
                            itemSubtotal
                    );

            product.setStock(
                    availableStock
                            - requestedQuantity
            );
        }

        if (drafts.isEmpty()) {
            throw new IllegalArgumentException(
                    "Checkout must contain at least one product"
            );
        }

        List<VendorOrderDraft> vendorDrafts =
                new ArrayList<>(drafts.values());

        BigDecimal checkoutSubtotal =
                vendorDrafts.stream()
                        .map(draft -> draft.subtotal)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        List<VendorOrderDraft> couponDrafts =
                findCouponEligibleDrafts(
                        coupon,
                        vendorDrafts
                );

        BigDecimal couponSubtotal =
                couponDrafts.stream()
                        .map(draft -> draft.subtotal)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        validateCouponForCheckout(
                coupon,
                user,
                couponSubtotal
        );

        BigDecimal checkoutDiscount =
                calculateCouponDiscount(
                        coupon,
                        couponSubtotal
                );

        for (VendorOrderDraft draft
                : vendorDrafts) {
            boolean freeShippingCoupon =
                    coupon != null
                            && coupon.getDiscountType()
                            == DiscountType.FREE_SHIPPING
                            && couponAppliesToDraft(
                            coupon,
                            draft
                    );

            draft.shippingFee =
                    calculateShippingFee(
                            draft.subtotal,
                            freeShippingCoupon
                    );
        }

        distributeDiscount(
                couponDrafts,
                checkoutDiscount
        );

        BigDecimal checkoutShipping =
                vendorDrafts.stream()
                        .map(draft -> draft.shippingFee)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        BigDecimal checkoutTotal =
                checkoutSubtotal
                        .add(checkoutShipping)
                        .subtract(checkoutDiscount)
                        .max(BigDecimal.ZERO)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        CheckoutGroup checkoutGroup =
                CheckoutGroup.builder()
                        .user(user)
                        .orderCount(
                                vendorDrafts.size()
                        )
                        .subtotalAmount(
                                checkoutSubtotal
                        )
                        .shippingFee(
                                checkoutShipping
                        )
                        .discountAmount(
                                checkoutDiscount
                        )
                        .totalAmount(
                                checkoutTotal
                        )
                        .couponCode(couponCode)
                        .build();

        CheckoutGroup savedCheckoutGroup =
                checkoutGroupRepository.save(
                        checkoutGroup
                );

        List<OrderResponse> orderResponses =
                new ArrayList<>();

        int sequence = 1;

        for (VendorOrderDraft draft
                : vendorDrafts) {
            BigDecimal orderTotal =
                    draft.subtotal
                            .add(draft.shippingFee)
                            .subtract(draft.discount)
                            .max(BigDecimal.ZERO)
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            );

            Order order = Order.builder()
                    .user(user)
                    .checkoutGroup(
                            savedCheckoutGroup
                    )
                    .checkoutSequence(sequence)
                    .organization(
                            draft.organization
                    )
                    .organizationName(
                            draft.organization.getName()
                    )
                    .addressId(
                            request.getAddressId()
                    )
                    .shippingName(
                            request.getShippingName()
                                    .trim()
                    )
                    .shippingEmail(
                            request.getShippingEmail()
                                    .trim()
                                    .toLowerCase(
                                            Locale.ROOT
                                    )
                    )
                    .shippingPhone(
                            request.getShippingPhone()
                                    .trim()
                    )
                    .shippingAddress(
                            request.getShippingAddress()
                                    .trim()
                    )
                    .subtotalAmount(
                            draft.subtotal
                    )
                    .shippingFee(
                            draft.shippingFee
                    )
                    .discountAmount(
                            draft.discount
                    )
                    .couponCode(
                            couponAppliesToDraft(
                                    coupon,
                                    draft
                            )
                                    ? couponCode
                                    : null
                    )
                    .totalAmount(orderTotal)
                    .status(OrderStatus.PENDING)
                    .build();

            Order savedOrder =
                    orderRepository.save(order);

            List<OrderItem> orderItems =
                    createOrderItems(
                            savedOrder,
                            draft.items
                    );

            orderItemRepository.saveAll(
                    orderItems
            );

            OrderStatusHistory history =
                    OrderStatusHistory.builder()
                            .order(savedOrder)
                            .fromStatus(null)
                            .toStatus(
                                    OrderStatus.PENDING
                            )
                            .changedByUser(user)
                            .build();

            orderStatusHistoryRepository.save(
                    history
            );

            Payment payment =
                    Payment.builder()
                            .order(savedOrder)
                            .method(
                                    PaymentMethod
                                            .CASH_ON_DELIVERY
                            )
                            .status(
                                    PaymentStatus.PENDING
                            )
                            .build();

            paymentRepository.save(payment);

            eventPublisher.publishEvent(
                    new OrderStatusChangedEvent(
                            savedOrder
                                    .getShippingEmail(),
                            savedOrder
                                    .getShippingName(),
                            savedOrder.getId(),
                            savedOrder.getStatus(),
                            savedOrder
                                    .getTotalAmount()
                    )
            );

            orderResponses.add(
                    OrderMapper.toResponse(
                            savedOrder,
                            orderItems,
                            payment
                    )
            );

            sequence++;
        }

        if (coupon != null) {
            CheckoutGroupCoupon checkoutCoupon =
                    CheckoutGroupCoupon.builder()
                            .checkoutGroup(
                                    savedCheckoutGroup
                            )
                            .coupon(coupon)
                            .discountAmount(
                                    checkoutDiscount
                            )
                            .build();

            checkoutGroupCouponRepository.save(
                    checkoutCoupon
            );
        }

        return CheckoutResponse.builder()
                .checkoutReference(
                        savedCheckoutGroup
                                .getCheckoutReference()
                )
                .userId(user.getId())
                .orderCount(
                        savedCheckoutGroup
                                .getOrderCount()
                )
                .subtotalAmount(
                        checkoutSubtotal
                )
                .shippingFee(
                        checkoutShipping
                )
                .discountAmount(
                        checkoutDiscount
                )
                .totalAmount(checkoutTotal)
                .couponCode(couponCode)
                .createdAt(
                        savedCheckoutGroup
                                .getCreatedAt()
                )
                .orders(orderResponses)
                .build();
    }

    private Map<Long, Integer>
    mergeRequestedQuantities(
            List<CreateOrderItemRequest> items
    ) {
        Map<Long, Integer> quantities =
                new LinkedHashMap<>();

        for (CreateOrderItemRequest item : items) {
            quantities.merge(
                    item.getProductId(),
                    item.getQuantity(),
                    Math::addExact
            );
        }

        return quantities;
    }

    private List<OrderItem> createOrderItems(
            Order order,
            List<PreparedItem> preparedItems
    ) {
        List<OrderItem> orderItems =
                new ArrayList<>();

        for (PreparedItem prepared
                : preparedItems) {
            Product product = prepared.product();

            orderItems.add(
                    OrderItem.builder()
                            .order(order)
                            .productId(
                                    product.getId()
                            )
                            .productName(
                                    product.getName()
                            )
                            .productImage(null)
                            .variantInfo(null)
                            .unitPrice(
                                    prepared.unitPrice()
                            )
                            .quantity(
                                    prepared.quantity()
                            )
                            .subtotal(
                                    prepared.subtotal()
                            )
                            .build()
            );
        }

        return orderItems;
    }

    private BigDecimal calculateShippingFee(
            BigDecimal subtotal,
            boolean freeShippingCoupon
    ) {
        if (subtotal.signum() == 0
                || subtotal.compareTo(
                FREE_SHIPPING_THRESHOLD
        ) > 0
                || freeShippingCoupon) {
            return BigDecimal.ZERO
                    .setScale(2);
        }

        return STANDARD_SHIPPING_FEE;
    }

    private void distributeDiscount(
            List<VendorOrderDraft> drafts,
            BigDecimal totalDiscount
    ) {
        if (totalDiscount.signum() == 0) {
            for (VendorOrderDraft draft : drafts) {
                draft.discount =
                        BigDecimal.ZERO.setScale(2);
            }
            return;
        }

        BigDecimal totalSubtotal =
                drafts.stream()
                        .map(draft -> draft.subtotal)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal allocated =
                BigDecimal.ZERO.setScale(2);

        for (VendorOrderDraft draft : drafts) {
            draft.discount =
                    totalDiscount
                            .multiply(draft.subtotal)
                            .divide(
                                    totalSubtotal,
                                    2,
                                    RoundingMode.DOWN
                            )
                            .min(draft.subtotal);

            allocated =
                    allocated.add(
                            draft.discount
                    );
        }

        BigDecimal remaining =
                totalDiscount
                        .subtract(allocated)
                        .setScale(2);

        while (remaining.signum() > 0) {
            boolean distributed = false;

            for (VendorOrderDraft draft : drafts) {
                if (remaining.signum() <= 0) {
                    break;
                }

                BigDecimal available =
                        draft.subtotal.subtract(
                                draft.discount
                        );

                if (available.signum() <= 0) {
                    continue;
                }

                BigDecimal increment =
                        remaining.min(ONE_CENT)
                                .min(available);

                draft.discount =
                        draft.discount.add(
                                increment
                        );

                remaining =
                        remaining.subtract(
                                increment
                        );

                distributed = true;
            }

            if (!distributed) {
                throw new IllegalStateException(
                        "Unable to distribute checkout discount"
                );
            }
        }
    }

    private List<VendorOrderDraft>
    findCouponEligibleDrafts(
            Coupon coupon,
            List<VendorOrderDraft> vendorDrafts
    ) {
        if (coupon == null
                || coupon.getOrganization() == null) {
            return vendorDrafts;
        }

        List<VendorOrderDraft> eligibleDrafts =
                vendorDrafts.stream()
                        .filter(draft ->
                                couponAppliesToDraft(
                                        coupon,
                                        draft
                                )
                        )
                        .toList();

        if (eligibleDrafts.isEmpty()) {
            throw new IllegalArgumentException(
                    "Coupon does not apply to products "
                            + "in this checkout"
            );
        }

        return eligibleDrafts;
    }

    private boolean couponAppliesToDraft(
            Coupon coupon,
            VendorOrderDraft draft
    ) {
        if (coupon == null || draft == null) {
            return false;
        }

        if (coupon.getOrganization() == null) {
            return true;
        }

        return draft.organization != null
                && draft.organization.getId() != null
                && draft.organization.getId().equals(
                coupon.getOrganization().getId()
        );
    }
    private String normalizeCouponCode(
            String couponCode
    ) {
        if (couponCode == null) {
            return null;
        }

        String normalized =
                couponCode.trim()
                        .toUpperCase(Locale.ROOT);

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private Coupon findCouponForCheckout(
            String couponCode
    ) {
        if (couponCode == null) {
            return null;
        }

        Coupon coupon = couponRepository
                .findByCodeForUpdate(couponCode)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Invalid coupon code"
                        )
                );

        if (coupon.getStatus()
                != CouponStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Coupon is inactive"
            );
        }

        LocalDateTime now =
                LocalDateTime.now();

        if (coupon.getStartDate() != null
                && now.isBefore(
                coupon.getStartDate()
        )) {
            throw new IllegalArgumentException(
                    "Coupon is not active yet"
            );
        }

        if (coupon.getEndDate() != null
                && now.isAfter(
                coupon.getEndDate()
        )) {
            throw new IllegalArgumentException(
                    "Coupon has expired"
            );
        }

        return coupon;
    }

    private void validateCouponForCheckout(
            Coupon coupon,
            User user,
            BigDecimal subtotal
    ) {
        if (coupon == null) {
            return;
        }

        BigDecimal minimumAmount =
                coupon.getMinimumOrderAmount()
                        == null
                        ? BigDecimal.ZERO
                        : coupon
                        .getMinimumOrderAmount();

        if (subtotal.compareTo(
                minimumAmount
        ) < 0) {
            throw new IllegalArgumentException(
                    "Minimum order amount for coupon "
                            + "is not reached"
            );
        }

        if (coupon.getUsageLimit() != null) {
            long legacyUsages =
                    orderCouponRepository
                            .countUsagesExcludingStatus(
                                    coupon.getId(),
                                    OrderStatus.CANCELLED
                            );

            long checkoutUsages =
                    checkoutGroupCouponRepository
                            .countUsagesExcludingStatus(
                                    coupon.getId(),
                                    OrderStatus.CANCELLED
                            );

            if (legacyUsages + checkoutUsages
                    >= coupon.getUsageLimit()) {
                throw new IllegalArgumentException(
                        "Coupon usage limit has been reached"
                );
            }
        }

        if (coupon.getPerUserLimit() != null) {
            long legacyUserUsages =
                    orderCouponRepository
                            .countUserUsagesExcludingStatus(
                                    coupon.getId(),
                                    user.getId(),
                                    OrderStatus.CANCELLED
                            );

            long checkoutUserUsages =
                    checkoutGroupCouponRepository
                            .countUserUsagesExcludingStatus(
                                    coupon.getId(),
                                    user.getId(),
                                    OrderStatus.CANCELLED
                            );

            if (legacyUserUsages
                    + checkoutUserUsages
                    >= coupon.getPerUserLimit()) {
                throw new IllegalArgumentException(
                        "Coupon usage limit for this user "
                                + "has been reached"
                );
            }
        }
    }

    private BigDecimal calculateCouponDiscount(
            Coupon coupon,
            BigDecimal subtotal
    ) {
        if (coupon == null) {
            return BigDecimal.ZERO.setScale(2);
        }

        BigDecimal discount =
                switch (coupon.getDiscountType()) {
                    case PERCENTAGE ->
                            subtotal.multiply(
                                    coupon.getDiscountValue()
                            ).divide(
                                    new BigDecimal("100"),
                                    2,
                                    RoundingMode.HALF_UP
                            );

                    case FIXED ->
                            coupon.getDiscountValue();

                    case FREE_SHIPPING ->
                            BigDecimal.ZERO;
                };

        return discount
                .max(BigDecimal.ZERO)
                .min(subtotal)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private User getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication
                .isAuthenticated()) {
            throw new IllegalStateException(
                    "Authenticated user is required"
            );
        }

        String usernameOrEmail =
                authentication.getName();

        return userRepository
                .findByEmail(usernameOrEmail)
                .or(
                        () -> userRepository
                                .findByUsername(
                                        usernameOrEmail
                                )
                )
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "User not found"
                        )
                );
    }

    private record PreparedItem(
            Product product,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }

    private static final class VendorOrderDraft {

        private final Organization organization;

        private final List<PreparedItem> items =
                new ArrayList<>();

        private BigDecimal subtotal =
                BigDecimal.ZERO;

        private BigDecimal shippingFee =
                BigDecimal.ZERO.setScale(2);

        private BigDecimal discount =
                BigDecimal.ZERO.setScale(2);

        private VendorOrderDraft(
                Organization organization
        ) {
            this.organization = organization;
        }
    }
}