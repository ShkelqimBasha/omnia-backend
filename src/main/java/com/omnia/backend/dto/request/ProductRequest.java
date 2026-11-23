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

    /*
     * Temporarily optional for compatibility with
     * existing products and the current Android app.
     */
    @Positive
    private Long organizationId;

    private String brand;

    /** Category-specific fields, e.g. {"size":"M"} or {"year":2018}. */
    private java.util.Map<String, Object> attributes;

    /** Cars only: CONTACT_ONLY (default) or CONTACT_AND_BUY. */
    private String saleMode;
}