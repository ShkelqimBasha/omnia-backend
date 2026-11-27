package com.omnia.backend.dto.request;

import com.omnia.backend.enums.OrganizationMemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationMemberAccountRequest {

    @NotBlank
    private String firstName;

    private String lastName;

    @NotBlank
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @jakarta.validation.constraints.Size(min = 8, max = 72,
            message = "Password must contain between 8 and 72 characters")
    private String password;

    private String phone;

    @NotNull
    private OrganizationMemberRole membershipRole;

    private boolean attachExisting;
}