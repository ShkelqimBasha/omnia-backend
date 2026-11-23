package com.omnia.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The cart lines whose allowed payment methods the buyer wants to know. */
public record PaymentOptionsRequest(
        @NotEmpty @Size(max = 100) List<@Valid CreateOrderItemRequest> items
) {
}
