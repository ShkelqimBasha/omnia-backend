package com.omnia.backend.service.interfaces;

import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.UserRoleUpdateRequest;
import com.omnia.backend.dto.request.UserStatusUpdateRequest;
import com.omnia.backend.dto.response.AdminUserResponse;
import com.omnia.backend.dto.response.AdminUserStatsResponse;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.dto.request.AdminCreateUserRequest;

public interface AdminUserService {
    AdminUserResponse createUser(
            AdminCreateUserRequest request
    );

    PagedResponse<AdminUserResponse> getUsers(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String keyword,
            UserStatus status,
            String role
    );

    AdminUserResponse getUserById(
            Long userId
    );

    AdminUserResponse updateUserStatus(
            Long userId,
            UserStatusUpdateRequest request
    );

    AdminUserResponse updateUserRole(
            Long userId,
            UserRoleUpdateRequest request
    );

    void deleteUser(Long userId);

    AdminUserStatsResponse getStats();
}