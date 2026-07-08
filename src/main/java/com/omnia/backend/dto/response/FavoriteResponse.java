package com.omnia.backend.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FavoriteResponse {

    private Long id;

    private Long productId;

    private String productName;

    private String brand;

    private BigDecimal price;

    private BigDecimal discountPrice;

    private String category;

    private String status;
}