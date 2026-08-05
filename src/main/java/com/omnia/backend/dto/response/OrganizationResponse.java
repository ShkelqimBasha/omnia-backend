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

    private String name;

    private String slug;

    private String description;

    private String status;

    private Long createdByUserId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}