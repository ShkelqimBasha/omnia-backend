package com.omnia.backend.controller;

import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.CreatePayoutRequest;
import com.omnia.backend.dto.request.SettleAdjustmentRequest;
import com.omnia.backend.dto.response.PayoutAdjustmentResponse;
import com.omnia.backend.dto.response.PayoutBalanceResponse;
import com.omnia.backend.dto.response.PayoutEligibleOrderResponse;
import com.omnia.backend.dto.response.SellerPayoutResponse;
import com.omnia.backend.dto.response.SellerPayoutSummaryResponse;
import com.omnia.backend.service.impl.SellerPayoutAdjustments;
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
    private final SellerPayoutAdjustments adjustments;

    public SellerPayoutController(SellerPayoutService service, SellerPayoutAdjustments adjustments) {
        this.service = service;
        this.adjustments = adjustments;
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

    /** Adjustments for refunds after a payout, newest first; status OPEN or SETTLED (optional). */
    @GetMapping("/api/admin/payouts/adjustments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public PagedResponse<PayoutAdjustmentResponse> adjustments(
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adjustments.history(organizationId, status, page, size);
    }

    /** Closes an open adjustment outside a payout: recovered from the seller or waived, with a note. */
    @PostMapping(value = "/api/admin/payouts/adjustments/{adjustmentId}/settle", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public PayoutAdjustmentResponse settle(@PathVariable Long adjustmentId,
                                           @RequestBody SettleAdjustmentRequest request) {
        return adjustments.settle(adjustmentId, request);
    }

    /** The store's own view; access is checked in the service (order managers of the store, or admins). */
    @GetMapping("/api/organizations/{organizationId}/payouts")
    @PreAuthorize("isAuthenticated()")
    public SellerPayoutSummaryResponse seller(@PathVariable Long organizationId) {
        return service.sellerSummary(organizationId);
    }
}
