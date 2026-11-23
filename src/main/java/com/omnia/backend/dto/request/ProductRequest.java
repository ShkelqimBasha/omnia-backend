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

    /*
     * Required for products without sizes. For products with sizes the
     * server stores the sum of the sizes and ignores this value.
     */
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

    /** Category-specific fields, e.g. {"color":"E zezë"} or {"year":2018}; sizes go in "sizes". */
    private java.util.Map<String, Object> attributes;

    /** Cars only: CONTACT_ONLY (default) or CONTACT_AND_BUY. */
    private String saleMode;

    /*
     * Clothing and shoes only: the sizes with their own stock. On update the
     * list replaces the product's sizes; null leaves them unchanged.
     */
    @jakarta.validation.Valid
    @jakarta.validation.constraints.Size(max = com.omnia.backend.catalog.ProductSizes.MAX_SIZES,
            message = "At most 40 sizes per product")
    private java.util.List<SizeRequest> sizes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SizeRequest {

        @NotBlank
        private String value;

        @NotNull
        @PositiveOrZero
        @jakarta.validation.constraints.Max(com.omnia.backend.catalog.ProductSizes.MAX_STOCK)
        private Integer stock;
    }
}