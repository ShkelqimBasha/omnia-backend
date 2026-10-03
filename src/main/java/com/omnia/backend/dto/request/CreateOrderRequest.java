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

    private com.omnia.backend.enums.DeliveryMethod deliveryMethod;

    @jakarta.validation.constraints.DecimalMin("-90")
    @jakarta.validation.constraints.DecimalMax("90")
    private java.math.BigDecimal shippingLatitude;

    @jakarta.validation.constraints.DecimalMin("-180")
    @jakarta.validation.constraints.DecimalMax("180")
    private java.math.BigDecimal shippingLongitude;

    @jakarta.validation.constraints.DecimalMin("0")
    @jakarta.validation.constraints.Digits(integer = 5, fraction = 2)
    private java.math.BigDecimal expectedExpressSurcharge;

    @jakarta.validation.constraints.AssertTrue(message = "Both delivery coordinates are required together")
    public boolean isShippingLocationComplete() {
        return (shippingLatitude == null) == (shippingLongitude == null);
    }


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

    @Size(max = 50)
    private String couponCode;

    @Valid
    @NotEmpty
    private List<CreateOrderItemRequest> items;
}