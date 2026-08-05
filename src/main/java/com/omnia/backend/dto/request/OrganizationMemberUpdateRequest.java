package com.omnia.backend.dto.request;

import com.omnia.backend.enums.OrganizationMemberRole;
import com.omnia.backend.enums.OrganizationMemberStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationMemberUpdateRequest {

    @NotNull
    private OrganizationMemberRole membershipRole;

    @NotNull
    private OrganizationMemberStatus status;
}