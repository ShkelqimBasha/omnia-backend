package com.omnia.backend.controller;

import com.omnia.backend.service.impl.PayPalRecoveryService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** SUPER_ADMIN resolution of PayPal checkouts that need recovery. Evidence in responses is sanitized. */
@RestController
@RequestMapping(value = "/api/admin/paypal/recovery", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminPayPalRecoveryController {
    private final PayPalRecoveryService service;

    public AdminPayPalRecoveryController(PayPalRecoveryService service) {
        this.service = service;
    }

    @GetMapping
    public List<PayPalRecoveryService.Summary> list() {
        return service.list();
    }

    @GetMapping("/checkouts/{id}")
    public PayPalRecoveryService.Detail checkout(@PathVariable String id) {
        return service.detail(id);
    }

    @GetMapping("/orders/{orderId}")
    public PayPalRecoveryService.Detail order(@PathVariable @Positive long orderId) {
        return service.detailForOrder(orderId);
    }

    @PostMapping(value = "/checkouts/{id}/resolve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PayPalRecoveryService.ResolveResult resolve(@PathVariable String id,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                       @RequestBody PayPalRecoveryService.ResolveRequest request) {
        return service.resolve(id, idempotencyKey, request);
    }

    /** Close a manually handled review case with a documented outcome; evidence and history are kept. */
    @PostMapping(value = "/cases/{caseId}/close", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PayPalRecoveryService.CloseResult closeCase(@PathVariable @Positive long caseId,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                       @RequestBody PayPalRecoveryService.CloseRequest request) {
        return service.closeCase(caseId, idempotencyKey, request);
    }
}
