package com.omnia.backend.dto.request;

import com.omnia.backend.enums.PlatformRoleName;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRoleUpdateRequest {

    @NotNull
    private PlatformRoleName role;
}