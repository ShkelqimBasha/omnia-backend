package com.omnia.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageRequest {

    @NotNull(
            message = "Uploaded file id must not be null"
    )
    @Positive(
            message = "Uploaded file id must be positive"
    )
    private Long uploadedFileId;

    @Builder.Default
    private Boolean isPrimary = false;
}