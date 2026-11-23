package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.dto.response.CheckoutResponse;
import com.omnia.backend.enums.PaymentMethod;

public interface CheckoutService {

    /** A cash-on-delivery checkout. */
    default CheckoutResponse checkout(
            CreateOrderRequest request
    ) {
        return checkout(request, PaymentMethod.CASH_ON_DELIVERY);
    }

    /**
     * Creates the orders, refusing a payment method that not every company in the
     * checkout accepts. Payments are still created as cash on delivery; the PayPal
     * flow switches them to PayPal afterwards.
     */
    CheckoutResponse checkout(
            CreateOrderRequest request,
            PaymentMethod paymentMethod
    );
}
