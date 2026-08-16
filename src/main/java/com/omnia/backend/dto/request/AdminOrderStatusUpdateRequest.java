package com.omnia.backend.dto.request;

import com.omnia.backend.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdminOrderStatusUpdateRequest {

    @NotNull(message = "Order status is required")
    private OrderStatus status;
}