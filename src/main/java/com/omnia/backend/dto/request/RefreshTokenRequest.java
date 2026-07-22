package com.omnia.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class RefreshTokenRequest {

    @Schema(
            accessMode = Schema.AccessMode.WRITE_ONLY,
            description = "Opaque refresh token"
    )
    @NotBlank(message = "Refresh token must not be blank")
    @Size(
            max = 512,
            message = "Refresh token must not exceed 512 characters"
    )
    private String refreshToken;
}