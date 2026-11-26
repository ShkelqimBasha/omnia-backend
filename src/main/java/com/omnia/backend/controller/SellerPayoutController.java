package com.omnia.backend.controller;

import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.CreatePayoutRequest;
import com.omnia.backend.dto.response.PayoutBalanceResponse;
import com.omnia.backend.dto.response.PayoutEligibleOrderResponse;
import com.omnia.backend.dto.response.SellerPayoutResponse;
import com.omnia.backend.dto.response.SellerPayoutSummaryResponse;
import com.omnia.backend.service.impl.SellerPayoutService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Seller payouts: what Omnia owes each seller for delivered PayPal orders, and the transfers made. */
@RestController
public class SellerPayoutController {

    private final SellerPayoutService service;

    public SellerPayoutController(SellerPayoutService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/payouts/balances")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public List<PayoutBalanceResponse> balances() {
        return service.balances();
    }

    @GetMapping("/api/admin/payouts/organizations/{organizationId}/eligible")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public List<PayoutEligibleOrderResponse> eligible(@PathVariable Long organizationId) {
        return service.eligibleOrders(organizationId);
    }

    @PostMapping(value = "/api/admin/payouts", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<SellerPayoutResponse> create(@RequestBody CreatePayoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/api/admin/payouts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public PagedResponse<SellerPayoutResponse> history(
            @RequestParam(required = false) Long organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.history(organizationId, page, size);
    }

    /** The store's own view; access is checked in the service (order managers of the store, or admins). */
    @GetMapping("/api/organizations/{organizationId}/payouts")
    @PreAuthorize("isAuthenticated()")
    public SellerPayoutSummaryResponse seller(@PathVariable Long organizationId) {
        return service.sellerSummary(organizationId);
    }
}
