package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.LoginRequest;
import com.omnia.backend.dto.request.RegisterRequest;
import com.omnia.backend.dto.response.AuthResponse;
import com.omnia.backend.dto.response.UserResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    UserResponse getCurrentUser();
}
