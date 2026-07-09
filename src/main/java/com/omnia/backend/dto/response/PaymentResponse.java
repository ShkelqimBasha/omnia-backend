package com.omnia.backend.dto.response;

import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.enums.PaymentStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long id;

    private Long orderId;

    private PaymentMethod method;

    private PaymentStatus status;

    private String transactionId;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;
}