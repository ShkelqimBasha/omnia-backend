package com.omnia.backend.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationResponse {

    private Long id;

    private String logoUrl;

    private String name;

    private String slug;

    private String description;

    private String status;

    /** "Shitës i verifikuar" badge. */
    private boolean verified;

    /** BUSINESS or PRIVATE ("Shitës privat"). */
    private String sellerType;

    private Long createdByUserId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}