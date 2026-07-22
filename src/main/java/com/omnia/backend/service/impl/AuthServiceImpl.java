package com.omnia.backend.service.impl;

import com.omnia.backend.mapper.UserMapper;
import com.omnia.backend.common.exception.EmailNotVerifiedException;
import com.omnia.backend.common.exception.InvalidCredentialsException;
import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.LoginRequest;
import com.omnia.backend.dto.request.RefreshTokenRequest;
import com.omnia.backend.dto.request.RegisterRequest;
import com.omnia.backend.dto.response.AuthResponse;
import com.omnia.backend.dto.response.UserResponse;
import com.omnia.backend.entity.RefreshToken;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.jwt.JwtService;
import com.omnia.backend.service.interfaces.AuthService;
import com.omnia.backend.service.interfaces.EmailVerificationService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;

    public AuthServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            EmailVerificationService emailVerificationService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        String normalizedEmail =
                request.getEmail().trim().toLowerCase();

        String normalizedUsername =
                request.getUsername().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResourceAlreadyExistsException(
                    "Email is already in use"
            );
        }

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new ResourceAlreadyExistsException(
                    "Username is already in use"
            );
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Default role USER not found"
                        )
                );

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(
                        request.getLastName() == null
                                ? null
                                : request.getLastName().trim()
                )
                .username(normalizedUsername)
                .email(normalizedEmail)
                .passwordHash(
                        passwordEncoder.encode(request.getPassword())
                )
                .phone(
                        request.getPhone() == null
                                ? null
                                : request.getPhone().trim()
                )
                .role(userRole)
                .emailVerified(false)
                .build();

        User savedUser = userRepository.save(user);

        emailVerificationService.createVerificationToken(savedUser);

        /*
         * Nuk krijojmë access token ose refresh token
         * derisa përdoruesi të verifikojë email-in.
         */
        return AuthResponse.builder()
                .accessToken(null)
                .refreshToken(null)
                .message(
                        "Registration successful. "
                                + "Please verify your email before logging in."
                )
                .build();
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {

        String usernameOrEmail =
                request.getUsernameOrEmail().trim();

        User user = userRepository
                .findByEmail(usernameOrEmail.toLowerCase())
                .or(() ->
                        userRepository.findByUsername(usernameOrEmail)
                )
                .orElseThrow(() ->
                        new InvalidCredentialsException(
                                "Invalid credentials"
                        )
                );

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        )) {
            throw new InvalidCredentialsException(
                    "Invalid credentials"
            );
        }

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new EmailNotVerifiedException(
                    "Please verify your email before logging in"
            );
        }

        String accessToken =
                jwtService.generateToken(user.getEmail());

        String refreshToken =
                refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .message("Login successful")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new InvalidCredentialsException(
                    "Unauthorized"
            );
        }

        String usernameOrEmail = authentication.getName();

        User user = userRepository
                .findByEmail(usernameOrEmail)
                .or(() ->
                        userRepository.findByUsername(usernameOrEmail)
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found"
                        )
                );

        return UserMapper.toResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(
            RefreshTokenRequest request
    ) {

        RefreshToken currentRefreshToken =
                refreshTokenService.verifyRefreshToken(
                        request.getRefreshToken()
                );

        User user = currentRefreshToken.getUser();

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            refreshTokenService.revokeAllUserTokens(user.getId());

            throw new EmailNotVerifiedException(
                    "Please verify your email before refreshing authentication"
            );
        }

        String newRefreshToken =
                refreshTokenService.createRefreshToken(user);

        String newAccessToken =
                jwtService.generateToken(user.getEmail());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .message("Token refreshed successfully")
                .build();
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {

        refreshTokenService.revokeToken(
                request.getRefreshToken()
        );
    }
}