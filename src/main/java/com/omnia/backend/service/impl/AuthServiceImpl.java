package com.omnia.backend.service.impl;

import com.omnia.backend.dto.request.RefreshTokenRequest;
import com.omnia.backend.common.exception.InvalidCredentialsException;
import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.dto.request.LoginRequest;
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

    public AuthServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceAlreadyExistsException(
                    "Email is already in use"
            );
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResourceAlreadyExistsException(
                    "Username is already in use"
            );
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() ->
                        new RuntimeException("Default role USER not found")
                );

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(userRole)
                .build();

        User savedUser = userRepository.save(user);

        String accessToken =
                jwtService.generateToken(savedUser.getEmail());

        RefreshToken refreshToken =
                refreshTokenService.createRefreshToken(savedUser);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .message("User registered successfully")
                .build();
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {

        User user = userRepository
                .findByEmail(request.getUsernameOrEmail())
                .or(() ->
                        userRepository.findByUsername(
                                request.getUsernameOrEmail()
                        )
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

        String accessToken =
                jwtService.generateToken(user.getEmail());

        RefreshToken refreshToken =
                refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
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
                || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthorized");
        }

        String usernameOrEmail = authentication.getName();

        User user = userRepository
                .findByEmail(usernameOrEmail)
                .or(() ->
                        userRepository.findByUsername(usernameOrEmail)
                )
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );

        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().getName())
                .build();
    }
    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {

        RefreshToken refreshToken =
                refreshTokenService.verifyRefreshToken(
                        request.getRefreshToken()
                );

        User user = refreshToken.getUser();

        String accessToken =
                jwtService.generateToken(user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
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