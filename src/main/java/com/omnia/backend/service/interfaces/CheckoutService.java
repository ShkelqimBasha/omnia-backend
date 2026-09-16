package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.dto.response.CheckoutResponse;

public interface CheckoutService {

    CheckoutResponse checkout(
            CreateOrderRequest request
    );
}