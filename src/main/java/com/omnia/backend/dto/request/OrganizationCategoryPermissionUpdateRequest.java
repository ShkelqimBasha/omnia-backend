package com.omnia.backend.dto.request;

import com.omnia.backend.enums.OrganizationPermissionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationCategoryPermissionUpdateRequest {

    @NotNull
    private Boolean canCreate;

    @NotNull
    private Boolean canUpdate;

    @NotNull
    private Boolean canDelete;

    @NotNull
    private OrganizationPermissionStatus status;
}