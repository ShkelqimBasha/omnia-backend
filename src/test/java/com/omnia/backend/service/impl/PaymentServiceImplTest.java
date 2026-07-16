package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.PaymentRequest;
import com.omnia.backend.dto.response.PaymentResponse;
import com.omnia.backend.entity.Order;
import com.omnia.backend.entity.Payment;
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.enums.PaymentStatus;
import com.omnia.backend.repository.OrderRepository;
import com.omnia.backend.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Order order;
    private Payment payment;
    private PaymentRequest request;

    @BeforeEach
    void setUp() {

        order = Order.builder()
                .id(1L)
                .addressId(10L)
                .totalAmount(new BigDecimal("1500.00"))
                .status(OrderStatus.PENDING)
                .build();

        payment = Payment.builder()
                .id(20L)
                .order(order)
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.PENDING)
                .transactionId(null)
                .paidAt(null)
                .createdAt(LocalDateTime.of(2026, 7, 16, 19, 30))
                .build();

        request = PaymentRequest.builder()
                .orderId(1L)
                .method(PaymentMethod.CARD)
                .build();
    }

    @Test
    void createPayment_shouldCreatePaymentSuccessfully() {

        when(paymentRepository.findByOrderId(1L))
                .thenReturn(Optional.empty());

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(payment);

        PaymentResponse result =
                paymentService.createPayment(request);

        assertNotNull(result);
        assertEquals(20L, result.getId());
        assertEquals(1L, result.getOrderId());
        assertEquals(PaymentMethod.CARD, result.getMethod());
        assertEquals(PaymentStatus.PENDING, result.getStatus());
        assertNull(result.getTransactionId());
        assertNull(result.getPaidAt());

        ArgumentCaptor<Payment> paymentCaptor =
                ArgumentCaptor.forClass(Payment.class);

        verify(paymentRepository)
                .save(paymentCaptor.capture());

        Payment savedPayment = paymentCaptor.getValue();

        assertEquals(order, savedPayment.getOrder());
        assertEquals(PaymentMethod.CARD, savedPayment.getMethod());
        assertEquals(PaymentStatus.PENDING, savedPayment.getStatus());
        assertNull(savedPayment.getTransactionId());
        assertNull(savedPayment.getPaidAt());

        verify(paymentRepository).findByOrderId(1L);
        verify(orderRepository).findById(1L);
    }

    @Test
    void createPayment_shouldThrowWhenPaymentAlreadyExists() {

        when(paymentRepository.findByOrderId(1L))
                .thenReturn(Optional.of(payment));

        ResourceAlreadyExistsException exception =
                assertThrows(
                        ResourceAlreadyExistsException.class,
                        () -> paymentService.createPayment(request)
                );

        assertEquals(
                "Payment already exists for this order",
                exception.getMessage()
        );

        verify(orderRepository, never())
                .findById(anyLong());

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    @Test
    void createPayment_shouldThrowWhenOrderDoesNotExist() {

        when(paymentRepository.findByOrderId(1L))
                .thenReturn(Optional.empty());

        when(orderRepository.findById(1L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> paymentService.createPayment(request)
                );

        assertEquals(
                "Order not found",
                exception.getMessage()
        );

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    @Test
    void getPaymentByOrder_shouldReturnPayment() {

        when(paymentRepository.findByOrderId(1L))
                .thenReturn(Optional.of(payment));

        PaymentResponse result =
                paymentService.getPaymentByOrder(1L);

        assertNotNull(result);
        assertEquals(20L, result.getId());
        assertEquals(1L, result.getOrderId());
        assertEquals(PaymentMethod.CARD, result.getMethod());
        assertEquals(PaymentStatus.PENDING, result.getStatus());
        assertEquals(
                LocalDateTime.of(2026, 7, 16, 19, 30),
                result.getCreatedAt()
        );

        verify(paymentRepository).findByOrderId(1L);
    }

    @Test
    void getPaymentByOrder_shouldThrowWhenPaymentDoesNotExist() {

        when(paymentRepository.findByOrderId(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> paymentService.getPaymentByOrder(99L)
                );

        assertEquals(
                "Payment not found",
                exception.getMessage()
        );
    }
}