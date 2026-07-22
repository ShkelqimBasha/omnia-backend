package com.omnia.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
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
public class ForgotPasswordRequest {

    @NotBlank(
            message = "Email must not be blank"
    )
    @Email(
            message = "Email format is invalid"
    )
    @Size(
            max = 254,
            message = "Email must not exceed 254 characters"
    )
    @Schema(
            example = "user@example.com"
    )
    private String email;
}