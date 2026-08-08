package com.omnia.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    private Long addressId;

    @NotBlank
    @Size(max = 150)
    private String shippingName;

    @NotBlank
    @Email
    @Size(max = 150)
    private String shippingEmail;

    @NotBlank
    @Size(max = 30)
    private String shippingPhone;

    @NotBlank
    @Size(max = 500)
    private String shippingAddress;

    @Valid
    @NotEmpty
    private List<CreateOrderItemRequest> items;
}