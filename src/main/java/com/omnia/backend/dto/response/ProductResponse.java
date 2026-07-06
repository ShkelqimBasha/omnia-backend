package com.omnia.backend.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;

    private String name;

    private String description;

    private BigDecimal price;

    private BigDecimal discountPrice;

    private Integer stock;

    private String brand;

    private String category;

    private String status;
}