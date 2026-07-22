package com.omnia.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.file")
public record FileStorageProperties(

        @NotBlank(
                message = "File upload directory must not be blank"
        )
        String uploadDir,

        @NotNull(
                message = "Maximum file size must be configured"
        )
        DataSize maxSize
) {

    private static final long MAX_ALLOWED_BYTES =
            DataSize.ofMegabytes(20).toBytes();

    public FileStorageProperties {
        if (maxSize != null
                && (maxSize.toBytes() <= 0
                || maxSize.toBytes() > MAX_ALLOWED_BYTES)) {
            throw new IllegalArgumentException(
                    "Maximum file size must be between 1 byte and 20MB"
            );
        }
    }
}