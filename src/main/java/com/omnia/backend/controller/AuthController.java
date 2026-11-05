package com.omnia.backend.controller;

import com.omnia.backend.dto.request.ForgotPasswordRequest;
import com.omnia.backend.dto.request.LoginRequest;
import com.omnia.backend.dto.request.RefreshTokenRequest;
import com.omnia.backend.dto.request.RegisterRequest;
import com.omnia.backend.dto.request.ResendVerificationRequest;
import com.omnia.backend.dto.request.ResetPasswordRequest;
import com.omnia.backend.dto.response.AuthResponse;
import com.omnia.backend.dto.response.UserResponse;
import com.omnia.backend.service.interfaces.AuthService;
import com.omnia.backend.service.interfaces.EmailVerificationService;
import com.omnia.backend.service.interfaces.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    public AuthController(
            AuthService authService,
            EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService
    ) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.ok(
                authService.register(request)
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(
                authService.login(request)
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return ResponseEntity.ok(
                authService.refreshToken(request)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(
            @RequestParam String token,
            @RequestHeader org.springframework.http.HttpHeaders headers
    ) {
        boolean browser = headers.getAccept().stream().anyMatch(type ->
                type.getQualityValue() > 0
                && "text".equals(type.getType()) && "html".equals(type.getSubtype()));
        emailVerificationService.verifyEmail(token);
        if (!browser) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("text/html;charset=UTF-8"))
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body("""
                        <!doctype html>
                        <html lang="sq">
                        <head>
                          <meta charset="utf-8">
                          <meta name="viewport" content="width=device-width, initial-scale=1">
                          <title>Email-i u verifikua | Omnia</title>
                          <style>
                            body{margin:0;background:#0b1423;color:#f3f6fb;font:1rem system-ui,sans-serif}
                            main{max-width:28rem;margin:12vh auto;padding:1.5rem}
                            a{display:inline-block;padding:.8rem 1.2rem;border-radius:.65rem;background:#6746d8;color:white}
                          </style>
                        </head>
                        <body><main>
                          <h1>Email-i u verifikua</h1>
                          <p>Tani mund të hysh në llogarinë tënde.</p>
                          <a href="omnia://email-verified">Hap Omnia</a>
                          <p>Nëse aplikacioni nuk hapet, hape Omnia nga telefoni dhe zgjidh Hyr.</p>
                        </main></body>
                        </html>
                        """);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request
    ) {
        emailVerificationService.resendVerificationEmail(
                request.getEmail()
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        passwordResetService.requestPasswordReset(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reset-password-link")
    public ResponseEntity<Void> openResetPassword(
            @RequestParam String token
    ) {
        URI appLink = UriComponentsBuilder
                .newInstance()
                .scheme("omnia")
                .host("reset-password")
                .queryParam("token", token)
                .build()
                .encode()
                .toUri();

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(appLink)
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser() {
        return ResponseEntity.ok(
                authService.getCurrentUser()
        );
    }
}
