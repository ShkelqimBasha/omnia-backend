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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceImplTest {

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmailVerificationServiceImpl verificationService;

    private User user;
    private EmailVerificationToken activeToken;

    @BeforeEach
    void setUp() {

        user = User.builder()
                .id(1L)
                .firstName("Shkelqim")
                .lastName("Basha")
                .username("shkelqim")
                .email("shkelqim@example.com")
                .emailVerified(false)
                .build();

        activeToken = EmailVerificationToken.builder()
                .id(10L)
                .user(user)
                .token("valid-verification-token")
                .expiresAt(LocalDateTime.now().plusHours(12))
                .used(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createVerificationToken_shouldCreateAndSendTokenSuccessfully() {

        when(tokenRepository.save(
                any(EmailVerificationToken.class)
        )).thenAnswer(invocation -> {
            EmailVerificationToken token =
                    invocation.getArgument(0);

            token.setId(10L);

            return token;
        });

        verificationService.createVerificationToken(user);

        verify(tokenRepository)
                .deleteByUserId(1L);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(
                        EmailVerificationToken.class
                );

        verify(tokenRepository)
                .save(tokenCaptor.capture());

        EmailVerificationToken savedToken =
                tokenCaptor.getValue();

        assertEquals(user, savedToken.getUser());
        assertNotNull(savedToken.getToken());
        assertFalse(savedToken.getToken().isBlank());
        assertFalse(savedToken.getUsed());
        assertNotNull(savedToken.getCreatedAt());
        assertNotNull(savedToken.getExpiresAt());

        assertTrue(
                savedToken.getExpiresAt()
                        .isAfter(
                                savedToken.getCreatedAt()
                                        .plusHours(23)
                        )
        );

        verify(emailService)
                .sendEmailVerification(
                        "shkelqim@example.com",
                        "Shkelqim Basha",
                        savedToken.getToken()
                );
    }

    @Test
    void createVerificationToken_shouldUseUsernameWhenNameIsMissing() {

        user.setFirstName(null);
        user.setLastName("   ");

        when(tokenRepository.save(
                any(EmailVerificationToken.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        verificationService.createVerificationToken(user);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(
                        EmailVerificationToken.class
                );

        verify(tokenRepository)
                .save(tokenCaptor.capture());

        verify(emailService)
                .sendEmailVerification(
                        "shkelqim@example.com",
                        "shkelqim",
                        tokenCaptor.getValue().getToken()
                );
    }

    @Test
    void createVerificationToken_shouldTrimRecipientName() {

        user.setFirstName("  Shkelqim  ");
        user.setLastName("  Basha  ");

        when(tokenRepository.save(
                any(EmailVerificationToken.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        verificationService.createVerificationToken(user);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(
                        EmailVerificationToken.class
                );

        verify(tokenRepository)
                .save(tokenCaptor.capture());

        verify(emailService)
                .sendEmailVerification(
                        "shkelqim@example.com",
                        "Shkelqim Basha",
                        tokenCaptor.getValue().getToken()
                );
    }

    @Test
    void createVerificationToken_shouldThrowWhenUserIsNull() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> verificationService
                                .createVerificationToken(null)
                );

        assertEquals(
                "User must not be null and must have a valid ID",
                exception.getMessage()
        );

        verifyNoInteractions(
                tokenRepository,
                userRepository,
                emailService
        );
    }

    @Test
    void createVerificationToken_shouldThrowWhenUserIdIsNull() {

        user.setId(null);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> verificationService
                                .createVerificationToken(user)
                );

        assertEquals(
                "User must not be null and must have a valid ID",
                exception.getMessage()
        );

        verifyNoInteractions(
                tokenRepository,
                userRepository,
                emailService
        );
    }

    @Test
    void createVerificationToken_shouldThrowWhenEmailAlreadyVerified() {

        user.setEmailVerified(true);

        EmailAlreadyVerifiedException exception =
                assertThrows(
                        EmailAlreadyVerifiedException.class,
                        () -> verificationService
                                .createVerificationToken(user)
                );

        assertEquals(
                "Email address is already verified",
                exception.getMessage()
        );

        verifyNoInteractions(
                tokenRepository,
                userRepository,
                emailService
        );
    }

    @Test
    void verifyEmail_shouldVerifyEmailSuccessfully() {

        when(tokenRepository.findByToken(
                "valid-verification-token"
        )).thenReturn(Optional.of(activeToken));

        when(userRepository.save(user))
                .thenReturn(user);

        when(tokenRepository.save(activeToken))
                .thenReturn(activeToken);

        verificationService.verifyEmail(
                "valid-verification-token"
        );

        assertTrue(user.getEmailVerified());
        assertTrue(activeToken.getUsed());

        verify(userRepository).save(user);
        verify(tokenRepository).save(activeToken);
    }

    @Test
    void verifyEmail_shouldThrowWhenTokenIsNull() {

        InvalidEmailVerificationTokenException exception =
                assertThrows(
                        InvalidEmailVerificationTokenException.class,
                        () -> verificationService.verifyEmail(null)
                );

        assertEquals(
                "Email verification token must not be blank",
                exception.getMessage()
        );

        verifyNoInteractions(
                tokenRepository,
                userRepository
        );
    }

    @Test
    void verifyEmail_shouldThrowWhenTokenIsBlank() {

        InvalidEmailVerificationTokenException exception =
                assertThrows(
                        InvalidEmailVerificationTokenException.class,
                        () -> verificationService.verifyEmail("   ")
                );

        assertEquals(
                "Email verification token must not be blank",
                exception.getMessage()
        );

        verifyNoInteractions(
                tokenRepository,
                userRepository
        );
    }

    @Test
    void verifyEmail_shouldThrowWhenTokenDoesNotExist() {

        when(tokenRepository.findByToken("invalid-token"))
                .thenReturn(Optional.empty());

        InvalidEmailVerificationTokenException exception =
                assertThrows(
                        InvalidEmailVerificationTokenException.class,
                        () -> verificationService.verifyEmail(
                                "invalid-token"
                        )
                );

        assertEquals(
                "Invalid email verification token",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void verifyEmail_shouldThrowWhenTokenWasAlreadyUsed() {

        activeToken.setUsed(true);

        when(tokenRepository.findByToken(
                "valid-verification-token"
        )).thenReturn(Optional.of(activeToken));

        InvalidEmailVerificationTokenException exception =
                assertThrows(
                        InvalidEmailVerificationTokenException.class,
                        () -> verificationService.verifyEmail(
                                "valid-verification-token"
                        )
                );

        assertEquals(
                "Email verification token has already been used",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);

        verify(tokenRepository, never())
                .save(any(EmailVerificationToken.class));
    }

    @Test
    void verifyEmail_shouldThrowWhenTokenIsExpired() {

        activeToken.setExpiresAt(
                LocalDateTime.now().minusMinutes(1)
        );

        when(tokenRepository.findByToken(
                "valid-verification-token"
        )).thenReturn(Optional.of(activeToken));

        EmailVerificationTokenExpiredException exception =
                assertThrows(
                        EmailVerificationTokenExpiredException.class,
                        () -> verificationService.verifyEmail(
                                "valid-verification-token"
                        )
                );

        assertEquals(
                "Email verification token has expired",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);

        verify(tokenRepository, never())
                .save(any(EmailVerificationToken.class));
    }

    @Test
    void verifyEmail_shouldThrowWhenUserEmailIsAlreadyVerified() {

        user.setEmailVerified(true);

        when(tokenRepository.findByToken(
                "valid-verification-token"
        )).thenReturn(Optional.of(activeToken));

        EmailAlreadyVerifiedException exception =
                assertThrows(
                        EmailAlreadyVerifiedException.class,
                        () -> verificationService.verifyEmail(
                                "valid-verification-token"
                        )
                );

        assertEquals(
                "Email address is already verified",
                exception.getMessage()
        );

        assertFalse(activeToken.getUsed());

        verifyNoInteractions(userRepository);

        verify(tokenRepository, never())
                .save(any(EmailVerificationToken.class));
    }

    @Test
    void resendVerificationEmail_shouldSendNewTokenSuccessfully() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        when(tokenRepository.save(
                any(EmailVerificationToken.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        verificationService.resendVerificationEmail(
                "  shkelqim@example.com  "
        );

        verify(userRepository)
                .findByEmail("shkelqim@example.com");

        verify(tokenRepository)
                .deleteByUserId(1L);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(
                        EmailVerificationToken.class
                );

        verify(tokenRepository)
                .save(tokenCaptor.capture());

        verify(emailService)
                .sendEmailVerification(
                        "shkelqim@example.com",
                        "Shkelqim Basha",
                        tokenCaptor.getValue().getToken()
                );
    }

    @Test
    void resendVerificationEmail_shouldThrowWhenEmailIsNull() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> verificationService
                                .resendVerificationEmail(null)
                );

        assertEquals(
                "Email must not be blank",
                exception.getMessage()
        );

        verifyNoInteractions(
                userRepository,
                tokenRepository,
                emailService
        );
    }

    @Test
    void resendVerificationEmail_shouldThrowWhenEmailIsBlank() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> verificationService
                                .resendVerificationEmail("   ")
                );

        assertEquals(
                "Email must not be blank",
                exception.getMessage()
        );

        verifyNoInteractions(
                userRepository,
                tokenRepository,
                emailService
        );
    }

    @Test
    void resendVerificationEmail_shouldThrowWhenUserDoesNotExist() {

        when(userRepository.findByEmail(
                "missing@example.com"
        )).thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> verificationService
                                .resendVerificationEmail(
                                        "missing@example.com"
                                )
                );

        assertEquals(
                "User not found with this email",
                exception.getMessage()
        );

        verifyNoInteractions(
                tokenRepository,
                emailService
        );
    }

    @Test
    void resendVerificationEmail_shouldThrowWhenEmailAlreadyVerified() {

        user.setEmailVerified(true);

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        EmailAlreadyVerifiedException exception =
                assertThrows(
                        EmailAlreadyVerifiedException.class,
                        () -> verificationService
                                .resendVerificationEmail(
                                        "shkelqim@example.com"
                                )
                );

        assertEquals(
                "Email address is already verified",
                exception.getMessage()
        );

        verifyNoInteractions(
                tokenRepository,
                emailService
        );
    }

    @Test
    void deleteExpiredTokens_shouldReturnDeletedCount() {

        when(tokenRepository.deleteExpiredTokens(
                any(LocalDateTime.class)
        )).thenReturn(5);

        int result =
                verificationService.deleteExpiredTokens();

        assertEquals(5, result);

        ArgumentCaptor<LocalDateTime> timeCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);

        verify(tokenRepository)
                .deleteExpiredTokens(timeCaptor.capture());

        LocalDateTime suppliedTime =
                timeCaptor.getValue();

        assertNotNull(suppliedTime);

        assertTrue(
                suppliedTime.isBefore(
                        LocalDateTime.now().plusSeconds(1)
                )
        );
    }

    @Test
    void deleteExpiredTokens_shouldReturnZeroWhenNothingDeleted() {

        when(tokenRepository.deleteExpiredTokens(
                any(LocalDateTime.class)
        )).thenReturn(0);

        int result =
                verificationService.deleteExpiredTokens();

        assertEquals(0, result);
    }
}