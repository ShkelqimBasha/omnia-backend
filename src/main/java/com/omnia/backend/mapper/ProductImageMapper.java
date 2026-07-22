package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.ProductImageResponse;
import com.omnia.backend.entity.ProductImage;
import com.omnia.backend.entity.UploadedFile;

public final class ProductImageMapper {

    private static final String FILE_ENDPOINT =
            "/api/files/";

    public static ProductImageResponse toResponse(
            ProductImage image
    ) {
        UploadedFile uploadedFile =
                image.getUploadedFile();

        Long uploadedFileId =
                uploadedFile == null
                        ? null
                        : uploadedFile.getId();

        String resolvedImageUrl =
                resolveImageUrl(
                        image,
                        uploadedFileId
                );

        return ProductImageResponse.builder()
                .id(image.getId())
                .productId(
                        image.getProduct().getId()
                )
                .uploadedFileId(uploadedFileId)
                .imageUrl(resolvedImageUrl)
                .isPrimary(
                        Boolean.TRUE.equals(
                                image.getIsPrimary()
                        )
                )
                .createdAt(image.getCreatedAt())
                .build();
    }

    private static String resolveImageUrl(
            ProductImage image,
            Long uploadedFileId
    ) {
        if (uploadedFileId != null) {
            return FILE_ENDPOINT + uploadedFileId;
        }

        /*
         * Compatibility only for rows created before
         * uploaded-file integration.
         */
        if (image.isLegacyUrlBacked()) {
            return image.getLegacyImageUrl();
        }

        throw new IllegalStateException(
                "Product image has no valid image source"
        );
    }

    private ProductImageMapper() {
    }
}