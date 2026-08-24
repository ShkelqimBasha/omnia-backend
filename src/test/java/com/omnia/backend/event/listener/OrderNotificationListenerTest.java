package com.omnia.backend.event.listener;

import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.event.OrderStatusChangedEvent;
import com.omnia.backend.service.interfaces.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderNotificationListenerTest {

    @Mock
    private EmailService emailService;

    private OrderNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener =
                new OrderNotificationListener(
                        emailService
                );
    }

    @Test
    void handleOrderStatusChanged_shouldSendDeliveredEmail() {

        OrderStatusChangedEvent event =
                new OrderStatusChangedEvent(
                        "buyer@example.com",
                        "Shkelqim Basha",
                        25L,
                        OrderStatus.DELIVERED,
                        new BigDecimal("39.40")
                );

        listener.handleOrderStatusChanged(event);

        verify(emailService)
                .sendOrderStatusEmail(
                        "buyer@example.com",
                        "Shkelqim Basha",
                        25L,
                        OrderStatus.DELIVERED,
                        new BigDecimal("39.40")
                );
    }

    @Test
    void handleOrderStatusChanged_shouldSkipProcessingEmail() {

        OrderStatusChangedEvent event =
                new OrderStatusChangedEvent(
                        "buyer@example.com",
                        "Shkelqim Basha",
                        25L,
                        OrderStatus.PROCESSING,
                        new BigDecimal("39.40")
                );

        listener.handleOrderStatusChanged(event);

        verifyNoInteractions(emailService);
    }

    @Test
    void handleOrderStatusChanged_shouldNotPropagateEmailFailure() {

        OrderStatusChangedEvent event =
                new OrderStatusChangedEvent(
                        "buyer@example.com",
                        "Shkelqim Basha",
                        25L,
                        OrderStatus.SHIPPED,
                        new BigDecimal("39.40")
                );

        doThrow(
                new RuntimeException("SMTP failure")
        ).when(emailService)
                .sendOrderStatusEmail(
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                );

        assertDoesNotThrow(
                () -> listener
                        .handleOrderStatusChanged(event)
        );

        verify(emailService)
                .sendOrderStatusEmail(
                        "buyer@example.com",
                        "Shkelqim Basha",
                        25L,
                        OrderStatus.SHIPPED,
                        new BigDecimal("39.40")
                );
    }
}