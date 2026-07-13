package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.InvalidPasswordResetTokenException;
import com.omnia.backend.common.exception.PasswordResetTokenExpiredException;
import com.omnia.backend.common.exception.PasswordResetTokenUsedException;
import com.omnia.backend.dto.request.ForgotPasswordRequest;
import com.omnia.backend.dto.request.ResetPasswordRequest;
import com.omnia.backend.entity.PasswordResetToken;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.PasswordResetTokenRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.interfaces.EmailService;
import com.omnia.backend.service.interfaces.PasswordResetService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final long TOKEN_EXPIRATION_MINUTES = 30;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;

    public PasswordResetServiceImpl(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            RefreshTokenService refreshTokenService
    ) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {

        String normalizedEmail =
                request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElse(null);

        /*
         * Nuk zbulojmë nëse email-i ekziston apo jo.
         * Kjo shmang user enumeration.
         */
        if (user == null) {
            return;
        }

        tokenRepository.deleteByUserId(user.getId());

        LocalDateTime now = LocalDateTime.now();

        PasswordResetToken resetToken =
                PasswordResetToken.builder()
                        .user(user)
                        .token(UUID.randomUUID().toString())
                        .createdAt(now)
                        .expiresAt(
                                now.plusMinutes(
                                        TOKEN_EXPIRATION_MINUTES
                                )
                        )
                        .used(false)
                        .build();

        PasswordResetToken savedToken =
                tokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(
                user.getEmail(),
                buildRecipientName(user),
                savedToken.getToken()
        );
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {

        if (!request.getNewPassword()
                .equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException(
                    "Passwords do not match"
            );
        }

        PasswordResetToken resetToken =
                tokenRepository.findByToken(request.getToken())
                        .orElseThrow(() ->
                                new InvalidPasswordResetTokenException(
                                        "Invalid password reset token"
                                )
                        );

        if (Boolean.TRUE.equals(resetToken.getUsed())) {
            throw new PasswordResetTokenUsedException(
                    "Password reset token has already been used"
            );
        }

        if (!resetToken.getExpiresAt()
                .isAfter(LocalDateTime.now())) {
            throw new PasswordResetTokenExpiredException(
                    "Password reset token has expired"
            );
        }

        User user = resetToken.getUser();

        user.setPasswordHash(
                passwordEncoder.encode(
                        request.getNewPassword()
                )
        );

        resetToken.setUsed(true);

        userRepository.save(user);
        tokenRepository.save(resetToken);

        refreshTokenService.revokeAllUserTokens(user.getId());
    }

    @Override
    @Transactional
    public int deleteExpiredTokens() {

        return tokenRepository.deleteExpiredTokens(
                LocalDateTime.now()
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
                (firstName + " " + lastName).trim();

        return fullName.isBlank()
                ? user.getUsername()
                : fullName;
    }
}