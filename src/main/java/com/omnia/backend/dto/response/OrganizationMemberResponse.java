package com.omnia.backend.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationMemberResponse {

    private Long id;

    private Long organizationId;

    private String organizationName;

    private Long userId;

    private String firstName;

    private String lastName;

    private String username;

    private String email;

    private String globalRole;

    private String membershipRole;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}