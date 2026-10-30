package com.omnia.backend.dto.request;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChangeCurrentPasswordRequest {
    @NotBlank @Size(max=512) private String currentPassword;
    @NotBlank @Size(min=8,max=72) private String newPassword;
}
