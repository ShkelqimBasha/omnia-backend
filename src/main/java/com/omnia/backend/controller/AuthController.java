package com.omnia.backend.controller;

import com.omnia.backend.dto.request.LoginRequest;
import com.omnia.backend.dto.request.RegisterRequest;
import com.omnia.backend.dto.response.AuthResponse;
import com.omnia.backend.service.interfaces.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.omnia.backend.dto.response.UserResponse;
import org.springframework.http.ResponseEntity;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser() {

        return ResponseEntity.ok(
                authService.getCurrentUser()
        );

    }
}