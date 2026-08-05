package com.omnia.backend.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserResponse {

    private Long id;

    private String firstName;

    private String lastName;

    private String username;

    private String email;

    private String phone;

    private String status;

    private Boolean emailVerified;

    private String platformRole;

    private Boolean organizationAdmin;

    private Boolean hasAdminAccess;

    private Long avatarFileId;

    private String avatarUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}