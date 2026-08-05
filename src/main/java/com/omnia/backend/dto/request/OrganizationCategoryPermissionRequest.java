package com.omnia.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationCategoryPermissionRequest {

    @NotNull
    @Positive
    private Long categoryId;

    @NotNull
    @Builder.Default
    private Boolean canCreate = true;

    @NotNull
    @Builder.Default
    private Boolean canUpdate = true;

    @NotNull
    @Builder.Default
    private Boolean canDelete = false;
}