package com.omnia.backend.controller;

import com.omnia.backend.dto.request.PaymentOptionsRequest;
import com.omnia.backend.dto.request.PaymentSettingsRequest;
import com.omnia.backend.dto.response.PaymentOptionsResponse;
import com.omnia.backend.dto.response.PaymentSettingsResponse;
import com.omnia.backend.service.impl.PaymentSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Accepted payment methods per company (company panel) and per cart (checkout). */
@RestController
@Validated
public class PaymentSettingsController {

    private final PaymentSettingsService service;

    public PaymentSettingsController(PaymentSettingsService service) {
        this.service = service;
    }

    @GetMapping("/api/organizations/{organizationId}/payment-settings")
    public PaymentSettingsResponse get(@PathVariable @Positive Long organizationId) {
        return service.get(organizationId);
    }

    @PutMapping("/api/organizations/{organizationId}/payment-settings")
    public PaymentSettingsResponse update(@PathVariable @Positive Long organizationId,
                                          @Valid @RequestBody PaymentSettingsRequest request) {
        return service.update(organizationId, request);
    }

    @PostMapping("/api/orders/checkout/payment-options")
    public PaymentOptionsResponse options(@Valid @RequestBody PaymentOptionsRequest request) {
        return service.options(request);
    }
}
