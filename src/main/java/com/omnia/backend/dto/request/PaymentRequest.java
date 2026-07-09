package com.omnia.backend.dto.request;

import com.omnia.backend.enums.PaymentMethod;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    private Long orderId;

    private PaymentMethod method;
}