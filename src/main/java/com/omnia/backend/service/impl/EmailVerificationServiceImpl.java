package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.EmailAlreadyVerifiedException;
import com.omnia.backend.common.exception.EmailVerificationTokenExpiredException;
import com.omnia.backend.common.exception.InvalidEmailVerificationTokenException;
import com.omnia.backend.config.EmailVerificationProperties;
import com.omnia.backend.entity.EmailVerificationToken;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.EmailVerificationTokenRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.service.SecureTokenService;
import com.omnia.backend.service.interfaces.EmailService;
import com.omnia.backend.service.interfaces.EmailVerificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@Transactional
public class EmailVerificationServiceImpl
        implements EmailVerificationService {

    private static final int MAX_TOKEN_LENGTH = 512;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final EmailVerificationProperties properties;
    private final Clock clock;
    private final SecureTokenService secureTokenService;

    public EmailVerificationServiceImpl(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            EmailService emailService,
            EmailVerificationProperties properties,
            Clock clock,
            SecureTokenService secureTokenService
    ) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.properties = properties;
        this.clock = clock;
        this.secureTokenService = secureTokenService;
    }

    @Override
    public void createVerificationToken(User user) {
        validateUser(user);

        if (Boolean.TRUE.equals(
                user.getEmailVerified()
        )) {
            throw new EmailAlreadyVerifiedException(
                    "Email address is already verified"
            );
        }

        tokenRepository.deleteByUserId(user.getId());

        LocalDateTime now = currentDateTime();

        String rawToken =
                secureTokenService.generateToken();

        EmailVerificationToken verificationToken =
                EmailVerificationToken.builder()
                        .user(user)
                        .tokenHash(
                                secureTokenService.hashToken(
                                        rawToken
                                )
                        )
                        .expiresAt(
                                now.plus(properties.lifetime())
                        )
                        .used(false)
                        .createdAt(now)
                        .build();

        tokenRepository.save(verificationToken);

        emailService.sendEmailVerification(
                user.getEmail(),
                buildRecipientName(user),
                rawToken
        );
    }

    @Override
    public void verifyEmail(String rawToken) {
        validateTokenValue(rawToken);

        String tokenHash =
                secureTokenService.hashToken(rawToken);

        EmailVerificationToken verificationToken =
                tokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(() ->
                                new InvalidEmailVerificationTokenException(
                                        "Invalid email verification token"
                                )
                        );

        if (Boolean.TRUE.equals(
                verificationToken.getUsed()
        )) {
            throw new InvalidEmailVerificationTokenException(
                    "Email verification token has already been used"
            );
        }

        if (!verificationToken.getExpiresAt()
                .isAfter(currentDateTime())) {
            throw new EmailVerificationTokenExpiredException(
                    "Email verification token has expired"
            );
        }

        User user = verificationToken.getUser();

        if (Boolean.TRUE.equals(
                user.getEmailVerified()
        )) {
            throw new EmailAlreadyVerifiedException(
                    "Email address is already verified"
            );
        }

        user.setEmailVerified(true);
        verificationToken.setUsed(true);

        userRepository.save(user);
        tokenRepository.save(verificationToken);
    }

    @Override
    public void resendVerificationEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email must not be blank"
            );
        }

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        userRepository.findByEmail(normalizedEmail)
                .filter(user ->
                        !Boolean.TRUE.equals(
                                user.getEmailVerified()
                        )
                )
                .ifPresent(this::createVerificationToken);
    }

    @Override
    public int deleteExpiredOrUsedTokens() {
        return tokenRepository
                .deleteExpiredOrUsedTokens(
                        currentDateTime()
                );
    }

    private void validateUser(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException(
                    "User must not be null and must have a valid ID"
            );
        }

        if (user.getEmail() == null
                || user.getEmail().isBlank()) {
            throw new IllegalArgumentException(
                    "User email must not be blank"
            );
        }
    }

    private void validateTokenValue(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidEmailVerificationTokenException(
                    "Email verification token must not be blank"
            );
        }

        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new InvalidEmailVerificationTokenException(
                    "Email verification token is too long"
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
                (firstName + " " + lastName).trim();

        return fullName.isBlank()
                ? user.getUsername()
                : fullName;
    }
}