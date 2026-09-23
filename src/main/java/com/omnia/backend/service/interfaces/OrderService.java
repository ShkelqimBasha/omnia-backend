package com.omnia.backend.service.interfaces;

import com.omnia.backend.enums.OrderStatus;

import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.dto.response.OrderResponse;
import com.omnia.backend.dto.response.OrderStatusHistoryResponse;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request);

    List<OrderResponse> getMyOrders();

    OrderResponse getOrderById(Long id);

    OrderResponse cancelMyOrder(Long id);

    List<OrderResponse> getOrdersForOrganization(
            Long organizationId
    );

    List<OrderStatusHistoryResponse>
    getOrderStatusHistoryForOrganization(
            Long organizationId,
            Long orderId
    );

    OrderResponse updateOrderStatusForOrganization(
            Long organizationId,
            Long orderId,
            OrderStatus status
    );
    List<OrderResponse> getAllOrdersForAdmin();

    OrderResponse updateOrderStatusForAdmin(
            Long id,
            com.omnia.backend.enums.OrderStatus status
    );
    List<OrderStatusHistoryResponse>
    getOrderStatusHistoryForAdmin(
            Long id
    );
}