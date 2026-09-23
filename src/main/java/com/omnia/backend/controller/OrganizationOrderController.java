package com.omnia.backend.controller;

import com.omnia.backend.dto.request.AdminOrderStatusUpdateRequest;
import com.omnia.backend.dto.response.OrderResponse;
import com.omnia.backend.dto.response.OrderStatusHistoryResponse;
import com.omnia.backend.service.interfaces.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/organizations/{organizationId}/orders"
)
@Validated
public class OrganizationOrderController {

    private final OrderService orderService;

    public OrganizationOrderController(
            OrderService orderService
    ) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>>
    getOrders(
            @PathVariable
            @Positive
            Long organizationId
    ) {
        return ResponseEntity.ok(
                orderService.getOrdersForOrganization(
                        organizationId
                )
        );
    }

    @GetMapping("/{orderId}/status-history")
    public ResponseEntity<
            List<OrderStatusHistoryResponse>
            >
    getOrderStatusHistory(
            @PathVariable
            @Positive
            Long organizationId,
            @PathVariable
            @Positive
            Long orderId
    ) {
        return ResponseEntity.ok(
                orderService
                        .getOrderStatusHistoryForOrganization(
                                organizationId,
                                orderId
                        )
        );
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse>
    updateOrderStatus(
            @PathVariable
            @Positive
            Long organizationId,
            @PathVariable
            @Positive
            Long orderId,
            @Valid
            @RequestBody
            AdminOrderStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(
                orderService
                        .updateOrderStatusForOrganization(
                                organizationId,
                                orderId,
                                request.getStatus()
                        )
        );
    }
}