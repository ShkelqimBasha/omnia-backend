package com.omnia.backend.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserStatsResponse {

    private Long totalUsers;

    private Long activeUsers;

    private Long inactiveUsers;

    private Long bannedUsers;

    private Long platformAdmins;

    private Long organizationAdmins;
}