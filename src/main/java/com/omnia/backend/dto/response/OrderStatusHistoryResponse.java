package com.omnia.backend.dto.response;

import com.omnia.backend.enums.OrderStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistoryResponse {

    private Long id;

    private Long orderId;

    private OrderStatus fromStatus;

    private OrderStatus toStatus;

    private Long changedByUserId;

    private String changedByName;

    private LocalDateTime changedAt;
}