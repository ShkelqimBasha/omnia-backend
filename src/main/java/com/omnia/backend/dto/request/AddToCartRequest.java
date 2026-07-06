package com.omnia.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddToCartRequest {

    @NotNull
    private Long productId;

    private Long variantId;

    @NotNull
    @Min(1)
    private Integer quantity;
}