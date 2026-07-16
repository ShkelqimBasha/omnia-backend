package com.omnia.backend.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {

        emailService = new EmailServiceImpl(mailSender);

        ReflectionTestUtils.setField(
                emailService,
                "senderEmail",
                "omnia@example.com"
        );

        ReflectionTestUtils.setField(
                emailService,
                "backendBaseUrl",
                "http://localhost:8080"
        );

        ReflectionTestUtils.setField(
                emailService,
                "resetPasswordUrl",
                "http://localhost:3000/reset-password"
        );
    }

    @Test
    void sendEmailVerification_shouldSendEmailSuccessfully() {

        emailService.sendEmailVerification(
                "user@example.com",
                "Shkelqim Basha",
                "verification-token"
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message =
                messageCaptor.getValue();

        assertEquals(
                "omnia@example.com",
                message.getFrom()
        );

        assertArrayEquals(
                new String[]{"user@example.com"},
                message.getTo()
        );

        assertEquals(
                "Verify your Omnia account",
                message.getSubject()
        );

        assertNotNull(message.getText());

        assertTrue(
                message.getText()
                        .contains("Hello Shkelqim Basha")
        );

        assertTrue(
                message.getText()
                        .contains(
                                "http://localhost:8080/api/auth/verify-email?token=verification-token"
                        )
        );

        assertTrue(
                message.getText()
                        .contains("This link expires in 24 hours")
        );

        assertTrue(
                message.getText()
                        .contains("Omnia Team")
        );
    }

    @Test
    void sendPasswordResetEmail_shouldSendEmailSuccessfully() {

        emailService.sendPasswordResetEmail(
                "user@example.com",
                "Shkelqim Basha",
                "reset-token"
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message =
                messageCaptor.getValue();

        assertEquals(
                "omnia@example.com",
                message.getFrom()
        );

        assertArrayEquals(
                new String[]{"user@example.com"},
                message.getTo()
        );

        assertEquals(
                "Reset your Omnia password",
                message.getSubject()
        );

        assertNotNull(message.getText());

        assertTrue(
                message.getText()
                        .contains("Hello Shkelqim Basha")
        );

        assertTrue(
                message.getText()
                        .contains(
                                "http://localhost:3000/reset-password?token=reset-token"
                        )
        );

        assertTrue(
                message.getText()
                        .contains("Your password reset token is:")
        );

        assertTrue(
                message.getText()
                        .contains("reset-token")
        );

        assertTrue(
                message.getText()
                        .contains("This link expires in 30 minutes")
        );
    }

    @Test
    void sendEmailVerification_shouldIncludeRecipientEmail() {

        emailService.sendEmailVerification(
                "another@example.com",
                "Another User",
                "token-123"
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(messageCaptor.capture());

        assertArrayEquals(
                new String[]{"another@example.com"},
                messageCaptor.getValue().getTo()
        );
    }

    @Test
    void sendPasswordResetEmail_shouldIncludeRecipientEmail() {

        emailService.sendPasswordResetEmail(
                "another@example.com",
                "Another User",
                "token-456"
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(messageCaptor.capture());

        assertArrayEquals(
                new String[]{"another@example.com"},
                messageCaptor.getValue().getTo()
        );
    }

    @Test
    void sendEmailVerification_shouldIncludeRecipientName() {

        emailService.sendEmailVerification(
                "user@example.com",
                "John Doe",
                "verification-token"
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(messageCaptor.capture());

        assertTrue(
                messageCaptor.getValue()
                        .getText()
                        .contains("Hello John Doe")
        );
    }

    @Test
    void sendPasswordResetEmail_shouldIncludeRecipientName() {

        emailService.sendPasswordResetEmail(
                "user@example.com",
                "John Doe",
                "reset-token"
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(messageCaptor.capture());

        assertTrue(
                messageCaptor.getValue()
                        .getText()
                        .contains("Hello John Doe")
        );
    }

    @Test
    void sendEmailVerification_shouldUseConfiguredBackendUrl() {

        ReflectionTestUtils.setField(
                emailService,
                "backendBaseUrl",
                "https://api.omnia.com"
        );

        emailService.sendEmailVerification(
                "user@example.com",
                "User",
                "abc123"
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(messageCaptor.capture());

        assertTrue(
                messageCaptor.getValue()
                        .getText()
                        .contains(
                                "https://api.omnia.com/api/auth/verify-email?token=abc123"
                        )
        );
    }

    @Test
    void sendPasswordResetEmail_shouldUseConfiguredResetUrl() {

        ReflectionTestUtils.setField(
                emailService,
                "resetPasswordUrl",
                "https://omnia.com/reset-password"
        );

        emailService.sendPasswordResetEmail(
                "user@example.com",
                "User",
                "xyz789"
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(messageCaptor.capture());

        assertTrue(
                messageCaptor.getValue()
                        .getText()
                        .contains(
                                "https://omnia.com/reset-password?token=xyz789"
                        )
        );
    }

    @Test
    void sendEmailVerification_shouldPropagateMailSenderException() {

        doThrow(new RuntimeException("SMTP failure"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> emailService.sendEmailVerification(
                                "user@example.com",
                                "User",
                                "token"
                        )
                );

        assertEquals(
                "SMTP failure",
                exception.getMessage()
        );
    }

    @Test
    void sendPasswordResetEmail_shouldPropagateMailSenderException() {

        doThrow(new RuntimeException("SMTP failure"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> emailService.sendPasswordResetEmail(
                                "user@example.com",
                                "User",
                                "token"
                        )
                );

        assertEquals(
                "SMTP failure",
                exception.getMessage()
        );
    }
}