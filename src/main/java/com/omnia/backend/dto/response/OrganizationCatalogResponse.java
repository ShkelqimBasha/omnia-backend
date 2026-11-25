package com.omnia.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationCatalogResponse {

    private Long id;

    private String logoUrl;

    private String name;

    private String slug;

    private String description;

    /** "Shitës i verifikuar" badge. */
    private boolean verified;

    /** BUSINESS or PRIVATE ("Shitës privat"). */
    private String sellerType;
}