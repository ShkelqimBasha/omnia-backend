package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.EmailAlreadyVerifiedException;
import com.omnia.backend.common.exception.EmailVerificationTokenExpiredException;
import com.omnia.backend.common.exception.InvalidEmailVerificationTokenException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.entity.EmailVerificationToken;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.EmailVerificationTokenRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.interfaces.EmailService;
import com.omnia.backend.service.interfaces.EmailVerificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class EmailVerificationServiceImpl
        implements EmailVerificationService {

    private static final long TOKEN_EXPIRATION_HOURS = 24;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public EmailVerificationServiceImpl(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            EmailService emailService
    ) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @Override
    public void createVerificationToken(User user) {

        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException(
                    "User must not be null and must have a valid ID"
            );
        }

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new EmailAlreadyVerifiedException(
                    "Email address is already verified"
            );
        }

        tokenRepository.deleteByUserId(user.getId());

        LocalDateTime now = LocalDateTime.now();

        EmailVerificationToken verificationToken =
                EmailVerificationToken.builder()
                        .user(user)
                        .token(UUID.randomUUID().toString())
                        .expiresAt(
                                now.plusHours(TOKEN_EXPIRATION_HOURS)
                        )
                        .used(false)
                        .createdAt(now)
                        .build();

        EmailVerificationToken savedToken =
                tokenRepository.save(verificationToken);

        String recipientName = buildRecipientName(user);

        emailService.sendEmailVerification(
                user.getEmail(),
                recipientName,
                savedToken.getToken()
        );
    }

    @Override
    public void verifyEmail(String token) {

        validateTokenValue(token);

        EmailVerificationToken verificationToken =
                tokenRepository.findByToken(token)
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
                .isAfter(LocalDateTime.now())) {
            throw new EmailVerificationTokenExpiredException(
                    "Email verification token has expired"
            );
        }

        User user = verificationToken.getUser();

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
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

        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with this email"
                        )
                );

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new EmailAlreadyVerifiedException(
                    "Email address is already verified"
            );
        }

        createVerificationToken(user);
    }

    @Override
    public int deleteExpiredTokens() {

        return tokenRepository.deleteExpiredTokens(
                LocalDateTime.now()
        );
    }

    private void validateTokenValue(String token) {

        if (token == null || token.isBlank()) {
            throw new InvalidEmailVerificationTokenException(
                    "Email verification token must not be blank"
            );
        }
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