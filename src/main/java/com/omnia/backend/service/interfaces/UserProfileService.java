package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.response.UserResponse;

public interface UserProfileService {

    UserResponse updateAvatar(Long uploadedFileId);

    UserResponse removeAvatar();
}