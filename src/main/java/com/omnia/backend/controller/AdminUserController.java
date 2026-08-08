package com.omnia.backend.controller;

import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.UserRoleUpdateRequest;
import com.omnia.backend.dto.request.UserStatusUpdateRequest;
import com.omnia.backend.dto.response.AdminUserResponse;
import com.omnia.backend.dto.response.AdminUserStatsResponse;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.service.interfaces.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.omnia.backend.dto.request.AdminCreateUserRequest;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/admin/users")
@Validated
@PreAuthorize(
        "hasAnyRole('SUPER_ADMIN', 'ADMIN')"
)
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(
            AdminUserService adminUserService
    ) {
        this.adminUserService = adminUserService;
    }
    @PostMapping
    public ResponseEntity<AdminUserResponse> createUser(
            @Valid
            @RequestBody
            AdminCreateUserRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        adminUserService.createUser(
                                request
                        )
                );
    }

    @GetMapping
    public ResponseEntity<PagedResponse<AdminUserResponse>>
    getUsers(
            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(defaultValue = "id")
            String sortBy,

            @RequestParam(defaultValue = "asc")
            String sortDir,

            @RequestParam(required = false)
            String keyword,

            @RequestParam(required = false)
            UserStatus status,

            @RequestParam(required = false)
            String role
    ) {
        return ResponseEntity.ok(
                adminUserService.getUsers(
                        page,
                        size,
                        sortBy,
                        sortDir,
                        keyword,
                        status,
                        role
                )
        );
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminUserStatsResponse>
    getStats() {

        return ResponseEntity.ok(
                adminUserService.getStats()
        );
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserResponse>
    getUserById(
            @PathVariable
            @Positive
            Long userId
    ) {
        return ResponseEntity.ok(
                adminUserService.getUserById(userId)
        );
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<AdminUserResponse>
    updateUserStatus(
            @PathVariable
            @Positive
            Long userId,

            @Valid
            @RequestBody
            UserStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(
                adminUserService.updateUserStatus(
                        userId,
                        request
                )
        );
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<AdminUserResponse>
    updateUserRole(
            @PathVariable
            @Positive
            Long userId,

            @Valid
            @RequestBody
            UserRoleUpdateRequest request
    ) {
        return ResponseEntity.ok(
                adminUserService.updateUserRole(
                        userId,
                        request
                )
        );
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable
            @Positive
            Long userId
    ) {
        adminUserService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}