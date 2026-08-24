package com.omnia.backend.event.listener;

import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.event.OrderStatusChangedEvent;
import com.omnia.backend.service.interfaces.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumSet;
import java.util.Set;

@Component
public class OrderNotificationListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    OrderNotificationListener.class
            );

    private static final Set<OrderStatus>
            NOTIFIABLE_STATUSES =
            EnumSet.of(
                    OrderStatus.PENDING,
                    OrderStatus.CONFIRMED,
                    OrderStatus.SHIPPED,
                    OrderStatus.DELIVERED,
                    OrderStatus.CANCELLED
            );

    private final EmailService emailService;

    public OrderNotificationListener(
            EmailService emailService
    ) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleOrderStatusChanged(
            OrderStatusChangedEvent event
    ) {
        if (!NOTIFIABLE_STATUSES.contains(
                event.status()
        )) {
            return;
        }

        try {
            emailService.sendOrderStatusEmail(
                    event.recipientEmail(),
                    event.recipientName(),
                    event.orderId(),
                    event.status(),
                    event.totalAmount()
            );
        } catch (Exception exception) {
            LOGGER.error(
                    "Failed to send order status email "
                            + "for order {} and status {}",
                    event.orderId(),
                    event.status(),
                    exception
            );
        }
    }
}