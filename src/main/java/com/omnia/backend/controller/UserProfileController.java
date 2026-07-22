package com.omnia.backend.controller;

import com.omnia.backend.dto.request.UserAvatarRequest;
import com.omnia.backend.dto.response.UserResponse;
import com.omnia.backend.service.interfaces.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@Validated
@PreAuthorize("isAuthenticated()")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(
            UserProfileService userProfileService
    ) {
        this.userProfileService =
                userProfileService;
    }

    @PutMapping("/avatar")
    public ResponseEntity<UserResponse> updateAvatar(
            @Valid
            @RequestBody
            UserAvatarRequest request
    ) {
        return ResponseEntity.ok(
                userProfileService.updateAvatar(
                        request.getUploadedFileId()
                )
        );
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<UserResponse> removeAvatar() {

        return ResponseEntity.ok(
                userProfileService.removeAvatar()
        );
    }
}