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
import com.omnia.backend.enums.OrderStatus;


import java.math.BigDecimal;

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
                "Verifiko email-in tënd në Omnia",
                message.getSubject()
        );

        assertNotNull(message.getText());

        assertTrue(
                message.getText()
                        .contains("Përshëndetje Shkelqim Basha")
        );

        assertTrue(
                message.getText()
                        .contains(
                                "http://localhost:8080/api/auth/verify-email?token=verification-token"
                        )
        );

        assertTrue(
                message.getText()
                        .contains("Linku skadon pas 24 orësh")
        );

        assertTrue(
                message.getText()
                        .contains("Ekipi Omnia")
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
                "Rivendos fjalëkalimin në Omnia",
                message.getSubject()
        );

        assertNotNull(message.getText());

        assertTrue(
                message.getText()
                        .contains("Përshëndetje Shkelqim Basha")
        );

        assertTrue(
                message.getText()
                        .contains(
                                "http://localhost:3000/reset-password?token=reset-token"
                        )
        );

        assertTrue(
                message.getText()
                        .contains("Kodi për rivendosje manuale:")
        );

        assertTrue(
                message.getText()
                        .contains("reset-token")
        );

        assertTrue(
                message.getText()
                        .contains("Linku skadon pas 30 minutash")
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
                        .contains("Përshëndetje John Doe")
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
                        .contains("Përshëndetje John Doe")
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
    void sendPasswordResetEmail_shouldAddWebLinkWhenConfigured() {

        ReflectionTestUtils.setField(
                emailService,
                "webResetPasswordUrl",
                "https://myomniaapp.com/fjalekalimi/rivendos"
        );

        emailService.sendPasswordResetEmail(
                "user@example.com",
                "User",
                "xyz789"
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(messageCaptor.capture());

        String text = messageCaptor.getValue().getText();

        assertTrue(text.contains(
                "http://localhost:3000/reset-password?token=xyz789"
        ));
        assertTrue(text.contains(
                "https://myomniaapp.com/fjalekalimi/rivendos?token=xyz789"
        ));
    }

    @Test
    void sendPasswordResetEmail_withoutWebUrl_shouldOnlyContainAppLink() {

        emailService.sendPasswordResetEmail(
                "user@example.com",
                "User",
                "xyz789"
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(messageCaptor.capture());

        assertFalse(messageCaptor.getValue().getText().contains("fjalekalimi"));
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
    @Test
    void sendOrderStatusEmail_shouldUseAlbanianStatusLabel() {

        emailService.sendOrderStatusEmail(
                "buyer@example.com",
                "Shkelqim Basha",
                25L,
                OrderStatus.DELIVERED,
                new BigDecimal("39.40")
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(
                        SimpleMailMessage.class
                );

        verify(mailSender)
                .send(messageCaptor.capture());

        SimpleMailMessage message =
                messageCaptor.getValue();

        assertEquals(
                "Porosia juaj Omnia u dorëzua",
                message.getSubject()
        );

        assertTrue(
                message.getText()
                        .contains(
                                "Statusi: E dorëzuar"
                        )
        );

        assertTrue(
                message.getText()
                        .contains(
                                "Totali: 39.40 €"
                        )
        );
    }


    @Test
    void sendInquiryEmail_titlesTheVisitWithTheListingAndListsTheBuyer() {
        emailService.sendInquiryEmail("zyra@example.com", new com.omnia.backend.event.InquiryCreatedEvent(
                7L, java.util.List.of("zyra@example.com"), "Prona Shpk", "Apartament 2+1", 42L, "VISIT",
                "Ana", "+355691112233", "Të shtunën?", java.time.LocalDate.of(2026, 12, 5),
                java.time.LocalDateTime.of(2026, 12, 5, 10, 30)));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("Kërkesë e re për vizitë: Apartament 2+1", message.getSubject());
        assertArrayEquals(new String[]{"zyra@example.com"}, message.getTo());
        assertTrue(message.getText().contains("Emri: Ana"));
        assertTrue(message.getText().contains("Telefoni: +355691112233"));
        assertTrue(message.getText().contains("Orari i zgjedhur: 05.12.2026 10:30"));
        assertTrue(message.getText().contains("Mesazhi: Të shtunën?"));
    }

    @Test
    void sendInquiryEmail_contactUsesTheContactTitleAndThePreferredDateIsOptional() {
        emailService.sendInquiryEmail("zyra@example.com", new com.omnia.backend.event.InquiryCreatedEvent(
                8L, java.util.List.of("zyra@example.com"), null, "Golf 7", 43L, "CONTACT",
                "Beni", "0692223344", null, null, null));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals("Kërkesë e re për kontakt: Golf 7", captor.getValue().getSubject());
        assertFalse(captor.getValue().getText().contains("Mesazhi"));
        assertFalse(captor.getValue().getText().contains("Data e dëshiruar"));
    }
}
