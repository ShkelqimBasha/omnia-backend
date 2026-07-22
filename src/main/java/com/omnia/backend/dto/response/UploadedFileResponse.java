package com.omnia.backend.dto.response;

import com.omnia.backend.entity.UploadedFile;

import java.time.LocalDateTime;

public record UploadedFileResponse(
        Long id,
        String originalName,
        String contentType,
        Long size,
        LocalDateTime uploadedAt,
        String url
) {

    public static UploadedFileResponse from(
            UploadedFile uploadedFile
    ) {
        if (uploadedFile == null) {
            throw new IllegalArgumentException(
                    "Uploaded file must not be null"
            );
        }

        return new UploadedFileResponse(
                uploadedFile.getId(),
                uploadedFile.getOriginalName(),
                uploadedFile.getContentType(),
                uploadedFile.getSize(),
                uploadedFile.getUploadedAt(),
                "/api/files/" + uploadedFile.getId()
        );
    }
}