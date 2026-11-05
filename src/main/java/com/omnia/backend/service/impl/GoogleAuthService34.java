package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.InvalidCredentialsException;
import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.dto.response.AuthResponse;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.jwt.JwtService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;

@Service
public class GoogleAuthService34 {
    private final GoogleTokenVerifier34 verifier;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final RefreshTokenService refresh;
    public GoogleAuthService34(GoogleTokenVerifier34 verifier, UserRepository users,
            RoleRepository roles, PasswordEncoder passwords, JwtService jwt,
            RefreshTokenService refresh) {
        this.verifier=verifier; this.users=users; this.roles=roles;
        this.passwords=passwords; this.jwt=jwt; this.refresh=refresh;
    }
    @Transactional
    public AuthResponse authenticate(String idToken, boolean privacyAccepted) {
        GoogleIdentity34 identity = verifier.verify(idToken);
        User user = users.findByGoogleUid34(identity.uid()).orElse(null);
        if (user == null) {
            if (users.existsByEmail(identity.email()))
                throw new ResourceAlreadyExistsException("Ky email ka nje llogari Omnia. Hyr me fjalekalimin ekzistues; lidhja me Google kerkon verifikim te vecante.");
            if (!privacyAccepted)
                throw new ResourceAlreadyExistsException("Per llogari te re, hap Regjistrohu dhe prano politiken e privatesise.");
            String name = identity.name() == null || identity.name().isBlank() ? "Google" : identity.name().trim();
            String label = name.replaceAll("[^A-Za-z0-9._-]+", "_");
            if (label.isBlank() || label.equals("_")) label="google";
            label=label.substring(0,Math.min(70,label.length()));
            user = User.builder().firstName(name.substring(0, Math.min(100,name.length())))
                    .lastName("").username(label+"_"+UUID.randomUUID().toString().substring(0,8))
                    .email(identity.email()).emailVerified(true).status(UserStatus.ACTIVE)
                    .role(roles.findByName("USER").orElseThrow(() -> new IllegalStateException("Default role missing")))
                    .passwordHash(passwords.encode(UUID.randomUUID().toString().replace("-", "")+UUID.randomUUID().toString().replace("-", "")))
                    .googleUid34(identity.uid()).build();
            user = users.saveAndFlush(user);
        }
        if (user.getStatus() != UserStatus.ACTIVE || !Boolean.TRUE.equals(user.getEmailVerified())
                || !user.getEmail().equalsIgnoreCase(identity.email()))
            throw new InvalidCredentialsException("Llogaria nuk mund te perdoret per hyrje me Google.");
        return AuthResponse.builder().accessToken(jwt.generateToken(user.getEmail()))
                .refreshToken(refresh.createRefreshToken(user)).message("Login successful").build();
    }
}
