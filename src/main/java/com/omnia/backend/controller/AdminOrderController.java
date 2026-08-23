package com.omnia.backend.controller;

import com.omnia.backend.dto.request.AdminOrderStatusUpdateRequest;
import com.omnia.backend.dto.response.OrderResponse;
import com.omnia.backend.dto.response.OrderStatusHistoryResponse;
import com.omnia.backend.service.interfaces.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/orders")
@Validated
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(
            OrderService orderService
    ) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>>
    getAllOrders() {

        return ResponseEntity.ok(
                orderService.getAllOrdersForAdmin()
        );
    }
    @GetMapping("/{id}/status-history")
    public ResponseEntity<
            List<OrderStatusHistoryResponse>
            >
    getOrderStatusHistory(
            @PathVariable
            @Positive
            Long id
    ) {
        return ResponseEntity.ok(
                orderService
                        .getOrderStatusHistoryForAdmin(
                                id
                        )
        );
    }


    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse>
    updateOrderStatus(
            @PathVariable
            @Positive
            Long id,
            @Valid
            @RequestBody
            AdminOrderStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(
                orderService.updateOrderStatusForAdmin(
                        id,
                        request.getStatus()
                )
        );
    }
}