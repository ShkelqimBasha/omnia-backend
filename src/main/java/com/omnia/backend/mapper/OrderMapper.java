package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.OrderItemResponse;
import com.omnia.backend.dto.response.OrderResponse;
import com.omnia.backend.entity.CheckoutGroup;
import com.omnia.backend.entity.Order;
import com.omnia.backend.entity.OrderItem;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.Payment;

import java.util.List;

public class OrderMapper {

    public static OrderItemResponse toItemResponse(
            OrderItem item
    ) {
        return OrderItemResponse.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .productImage(item.getProductImage())
                .variantInfo(item.getVariantInfo())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .build();
    }

    public static OrderResponse toResponse(
            Order order,
            List<OrderItem> items
    ) {
        return toResponse(order, items, null);
    }

    public static OrderResponse toResponse(
            Order order,
            List<OrderItem> items,
            Payment payment
    ) {
        CheckoutGroup checkoutGroup =
                order.getCheckoutGroup();

        Organization organization =
                order.getOrganization();

        String organizationName =
                order.getOrganizationName();

        if ((organizationName == null
                || organizationName.isBlank())
                && organization != null) {
            organizationName = organization.getName();
        }

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .checkoutReference(
                        checkoutGroup == null
                                ? null
                                : checkoutGroup
                                .getCheckoutReference()
                )
                .checkoutSequence(
                        order.getCheckoutSequence()
                )
                .checkoutOrderCount(
                        checkoutGroup == null
                                ? null
                                : checkoutGroup.getOrderCount()
                )
                .organizationId(
                        organization == null
                                ? null
                                : organization.getId()
                )
                .organizationName(organizationName)
                .addressId(order.getAddressId())
                .subtotalAmount(order.getSubtotalAmount())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .couponCode(order.getCouponCode())
                .shippingName(order.getShippingName())
                .shippingEmail(order.getShippingEmail())
                .shippingPhone(order.getShippingPhone())
                .shippingAddress(order.getShippingAddress())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .paymentMethod(
                        payment == null
                                ? null
                                : payment.getMethod()
                )
                .paymentStatus(
                        payment == null
                                ? null
                                : payment.getStatus()
                )
                .paidAt(
                        payment == null
                                ? null
                                : payment.getPaidAt()
                )
                .transactionId(
                        payment == null
                                ? null
                                : payment.getTransactionId()
                )
                .createdAt(order.getCreatedAt())
                .items(
                        items.stream()
                                .map(OrderMapper::toItemResponse)
                                .toList()
                )
                .build();
    }

    private OrderMapper() {
    }
}