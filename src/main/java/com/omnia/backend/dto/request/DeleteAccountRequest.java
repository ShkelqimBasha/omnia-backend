package com.omnia.backend.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of {@code DELETE /api/users/me}.
 *
 * Password accounts send {@code currentPassword}. Accounts signed in with Google
 * (which never chose a password) send {@code confirmGoogle: true} instead.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeleteAccountRequest {

    @Size(max = 512)
    private String currentPassword;

    private Boolean confirmGoogle;
}
