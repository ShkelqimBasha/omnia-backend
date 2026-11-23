package com.omnia.backend.event.listener;

import com.omnia.backend.event.InquiryCreatedEvent;
import com.omnia.backend.service.interfaces.EmailService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InquiryEmailListenerTest {

    private static InquiryCreatedEvent event(List<String> to) {
        return new InquiryCreatedEvent(1L, to, "Kompania", "Golf 7", 2L, "CONTACT",
                "Ana", "0691112233", null, null, null);
    }

    @Test
    void mailsEveryRecipientAndAFailureDoesNotStopTheOthers() {
        EmailService mail = mock(EmailService.class);
        doThrow(new RuntimeException("smtp down")).when(mail).sendInquiryEmail(eq("a@example.com"), any());
        new InquiryEmailListener(mail, true).onInquiryCreated(event(List.of("a@example.com", "b@example.com")));
        verify(mail).sendInquiryEmail(eq("b@example.com"), any());
    }

    @Test
    void canBeTurnedOff() {
        EmailService mail = mock(EmailService.class);
        new InquiryEmailListener(mail, false).onInquiryCreated(event(List.of("a@example.com")));
        verifyNoInteractions(mail);
    }
}
