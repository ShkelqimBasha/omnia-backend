package com.omnia.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResetPasswordRequest {

    @NotBlank(message = "Token must not be blank")
    private String token;

    @NotBlank(message = "New password must not be blank")
    @Size(
            min = 8,
            max = 100,
            message = "New password must contain between 8 and 100 characters"
    )
    private String newPassword;

    @NotBlank(message = "Password confirmation must not be blank")
    private String confirmPassword;
}