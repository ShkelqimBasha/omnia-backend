package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.PaymentRequest;
import com.omnia.backend.dto.response.PaymentResponse;

public interface PaymentService {

    PaymentResponse createPayment(PaymentRequest request);

    PaymentResponse getPaymentByOrder(Long orderId);

}