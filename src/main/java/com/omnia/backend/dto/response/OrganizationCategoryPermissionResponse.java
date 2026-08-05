package com.omnia.backend.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationCategoryPermissionResponse {

    private Long id;

    private Long organizationId;

    private String organizationName;

    private Long categoryId;

    private String categoryName;

    private Boolean canCreate;

    private Boolean canUpdate;

    private Boolean canDelete;

    private String status;

    private Long grantedByUserId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}