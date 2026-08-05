package com.omnia.backend.dto.request;

import com.omnia.backend.enums.OrganizationMemberRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationMemberRequest {

    @NotNull
    @Positive
    private Long userId;

    @NotNull
    private OrganizationMemberRole membershipRole;
}