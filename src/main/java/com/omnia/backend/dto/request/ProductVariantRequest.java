package com.omnia.backend.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantRequest {

    public static final int MAX_TEXT_LENGTH = 100;

    public static final int MAX_STOCK = 1_000_000;

    @NotBlank(message = "Variant name must not be blank")
    @Size(
            max = MAX_TEXT_LENGTH,
            message = "Variant name must be at most 100 characters"
    )
    private String variantName;

    @NotBlank(message = "Variant value must not be blank")
    @Size(
            max = MAX_TEXT_LENGTH,
            message = "Variant value must be at most 100 characters"
    )
    private String variantValue;

    /*
     * Optional (null is stored as 0.00); may be negative
     * for a cheaper variant. Must fit the DECIMAL(10,2) column.
     */
    @Digits(
            integer = 8,
            fraction = 2,
            message = "Price adjustment must have at most "
                    + "8 integer digits and 2 decimals"
    )
    private BigDecimal priceAdjustment;

    @NotNull(message = "Stock must not be null")
    @PositiveOrZero(message = "Stock must not be negative")
    @Max(
            value = MAX_STOCK,
            message = "Stock must be at most 1000000"
    )
    private Integer stock;
}
