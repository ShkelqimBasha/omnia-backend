package com.omnia.backend.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentUserOrganizationResponse {

    private Long organizationId;

    private String organizationName;

    private String organizationSlug;

    private String organizationStatus;

    /** BUSINESS or PRIVATE ("Shitës privat"). */
    private String organizationSellerType;

    private String membershipRole;

    private String membershipStatus;
}
