package com.omnia.backend.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageRequest {

    private String imageUrl;

    private Boolean isPrimary;
}