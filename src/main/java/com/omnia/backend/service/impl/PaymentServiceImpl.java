package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.PaymentRequest;
import com.omnia.backend.dto.response.PaymentResponse;
import com.omnia.backend.entity.Order;
import com.omnia.backend.entity.Payment;
import com.omnia.backend.enums.PaymentStatus;
import com.omnia.backend.mapper.PaymentMapper;
import com.omnia.backend.repository.OrderRepository;
import com.omnia.backend.repository.PaymentRepository;
import com.omnia.backend.service.interfaces.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {

        if (paymentRepository.findByOrderId(request.getOrderId()).isPresent()) {
            throw new ResourceAlreadyExistsException("Payment already exists for this order");
        }

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Order not found"));

        Payment payment = Payment.builder()
                .order(order)
                .method(request.getMethod())
                .status(PaymentStatus.PENDING)
                .transactionId(null)
                .paidAt(null)
                .build();

        Payment saved = paymentRepository.save(payment);

        return PaymentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrder(Long orderId) {

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payment not found"));

        return PaymentMapper.toResponse(payment);
    }
}