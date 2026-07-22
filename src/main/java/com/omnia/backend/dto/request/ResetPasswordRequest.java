package com.omnia.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class ResetPasswordRequest {

    @NotBlank(
            message = "Token must not be blank"
    )
    @Size(
            max = 512,
            message = "Token must not exceed 512 characters"
    )
    @Schema(
            accessMode = Schema.AccessMode.WRITE_ONLY
    )
    private String token;

    @NotBlank(
            message = "New password must not be blank"
    )
    @Size(
            min = 8,
            max = 100,
            message = "New password must contain between 8 and 100 characters"
    )
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)"
                    + "(?=.*[^A-Za-z\\d\\s])\\S{8,100}$",
            message = "New password must contain at least one uppercase letter, "
                    + "one lowercase letter, one number and one special character, "
                    + "without spaces"
    )
    @Schema(
            accessMode = Schema.AccessMode.WRITE_ONLY,
            example = "NewPassword123!"
    )
    private String newPassword;

    @NotBlank(
            message = "Password confirmation must not be blank"
    )
    @Size(
            min = 8,
            max = 100,
            message = "Password confirmation must contain between 8 and 100 characters"
    )
    @Schema(
            accessMode = Schema.AccessMode.WRITE_ONLY,
            example = "NewPassword123!"
    )
    private String confirmPassword;
}