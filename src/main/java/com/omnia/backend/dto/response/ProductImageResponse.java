package com.omnia.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageResponse {

    private Long id;

    private Long productId;

    private Long uploadedFileId;

    /*
     * URL used by Android or another client to retrieve
     * the image from the backend.
     */
    private String imageUrl;

    private Boolean isPrimary;

    private LocalDateTime createdAt;
}