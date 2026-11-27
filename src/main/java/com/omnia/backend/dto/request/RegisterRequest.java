package com.omnia.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    private String firstName;

    private String lastName;

    @NotBlank
    private String username;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    @jakarta.validation.constraints.Size(min = 8, max = 72,
            message = "Password must contain between 8 and 72 characters")
    private String password;

    private String phone;
}