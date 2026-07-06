package com.omnia.backend.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    @Positive
    private BigDecimal price;

    private BigDecimal discountPrice;

    @NotNull
    @PositiveOrZero
    private Integer stock;

    @NotNull
    private Long categoryId;

    private String brand;
}