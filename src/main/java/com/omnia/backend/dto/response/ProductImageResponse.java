package com.omnia.backend.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageResponse {

    private Long id;

    private Long productId;

    private String imageUrl;

    private Boolean isPrimary;

    private LocalDateTime createdAt;
}