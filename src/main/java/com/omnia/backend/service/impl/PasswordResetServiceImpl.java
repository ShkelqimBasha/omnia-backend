package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.InvalidPasswordResetTokenException;
import com.omnia.backend.common.exception.PasswordResetTokenExpiredException;
import com.omnia.backend.common.exception.PasswordResetTokenUsedException;
import com.omnia.backend.config.PasswordResetProperties;
import com.omnia.backend.dto.request.ForgotPasswordRequest;
import com.omnia.backend.dto.request.ResetPasswordRequest;
import com.omnia.backend.entity.PasswordResetToken;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.PasswordResetTokenRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.service.SecureTokenService;
import com.omnia.backend.service.interfaces.EmailService;
import com.omnia.backend.service.interfaces.PasswordResetService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@Transactional
public class PasswordResetServiceImpl
        implements PasswordResetService {

    private static final int MAX_TOKEN_LENGTH = 512;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetProperties properties;
    private final Clock clock;
    private final SecureTokenService secureTokenService;

    public PasswordResetServiceImpl(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            RefreshTokenService refreshTokenService,
            PasswordResetProperties properties,
            Clock clock,
            SecureTokenService secureTokenService
    ) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
        this.properties = properties;
        this.clock = clock;
        this.secureTokenService = secureTokenService;
    }

    @Override
    public void requestPasswordReset(
            ForgotPasswordRequest request
    ) {
        validateForgotPasswordRequest(request);

        String normalizedEmail =
                request.getEmail()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElse(null);

        /*
         * Nuk zbulojmë nëse email-i ekziston.
         * Endpoint-i kthen të njëjtën përgjigje
         * si për email ekzistues, ashtu edhe
         * për email të panjohur.
         */
        if (user == null) {
            return;
        }

        tokenRepository.deleteByUserId(
                user.getId()
        );

        LocalDateTime now = currentDateTime();

        String rawToken =
                secureTokenService.generateToken();

        PasswordResetToken resetToken =
                PasswordResetToken.builder()
                        .user(user)
                        .tokenHash(
                                secureTokenService.hashToken(
                                        rawToken
                                )
                        )
                        .createdAt(now)
                        .expiresAt(
                                now.plus(
                                        properties.lifetime()
                                )
                        )
                        .used(false)
                        .build();

        tokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(
                user.getEmail(),
                buildRecipientName(user),
                rawToken
        );
    }

    @Override
    public void resetPassword(
            ResetPasswordRequest request
    ) {
        validateResetPasswordRequest(request);

        String rawToken = request.getToken();

        PasswordResetToken resetToken =
                tokenRepository
                        .findByTokenHash(
                                secureTokenService.hashToken(
                                        rawToken
                                )
                        )
                        .orElseThrow(() ->
                                new InvalidPasswordResetTokenException(
                                        "Invalid password reset token"
                                )
                        );

        if (Boolean.TRUE.equals(
                resetToken.getUsed()
        )) {
            throw new PasswordResetTokenUsedException(
                    "Password reset token has already been used"
            );
        }

        if (!resetToken.getExpiresAt()
                .isAfter(currentDateTime())) {
            throw new PasswordResetTokenExpiredException(
                    "Password reset token has expired"
            );
        }

        User user = resetToken.getUser();

        if (passwordEncoder.matches(
                request.getNewPassword(),
                user.getPasswordHash()
        )) {
            throw new IllegalArgumentException(
                    "New password must be different from the current password"
            );
        }

        user.setPasswordHash(
                passwordEncoder.encode(
                        request.getNewPassword()
                )
        );

        resetToken.setUsed(true);

        userRepository.save(user);
        tokenRepository.save(resetToken);

        /*
         * Pas ndryshimit të password-it revokohen
         * të gjitha sesionet aktive të përdoruesit.
         */
        refreshTokenService.revokeAllUserTokens(
                user.getId()
        );
    }

    @Override
    public int deleteExpiredOrUsedTokens() {
        return tokenRepository
                .deleteExpiredOrUsedTokens(
                        currentDateTime()
                );
    }

    private void validateForgotPasswordRequest(
            ForgotPasswordRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Forgot password request must not be null"
            );
        }

        if (request.getEmail() == null
                || request.getEmail().isBlank()) {
            throw new IllegalArgumentException(
                    "Email must not be blank"
            );
        }
    }

    private void validateResetPasswordRequest(
            ResetPasswordRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Reset password request must not be null"
            );
        }

        validateTokenValue(request.getToken());

        if (request.getNewPassword() == null
                || request.getNewPassword().isBlank()) {
            throw new IllegalArgumentException(
                    "New password must not be blank"
            );
        }

        if (request.getConfirmPassword() == null
                || request.getConfirmPassword().isBlank()) {
            throw new IllegalArgumentException(
                    "Password confirmation must not be blank"
            );
        }

        if (!request.getNewPassword()
                .equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException(
                    "Passwords do not match"
            );
        }
    }

    private void validateTokenValue(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidPasswordResetTokenException(
                    "Password reset token must not be blank"
            );
        }

        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new InvalidPasswordResetTokenException(
                    "Password reset token is too long"
            );
        }
    }

    private LocalDateTime currentDateTime() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                clock.getZone()
        );
    }

    private String buildRecipientName(User user) {
        String firstName =
                user.getFirstName() == null
                        ? ""
                        : user.getFirstName().trim();

        String lastName =
                user.getLastName() == null
                        ? ""
                        : user.getLastName().trim();

        String fullName =
                (firstName + " " + lastName)
                        .trim();

        return fullName.isBlank()
                ? user.getUsername()
                : fullName;
    }
}