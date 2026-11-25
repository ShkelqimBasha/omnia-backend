package com.omnia.backend.controller;

import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.SellerApplicationRequest;
import com.omnia.backend.dto.response.SellerApplicationResponse;
import com.omnia.backend.enums.SellerApplicationStatus;
import com.omnia.backend.service.impl.SellerApplicationService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "Bëhu shitës": the applicant side and the platform admin review. */
@RestController
@Validated
public class SellerApplicationController {

    private final SellerApplicationService service;

    public SellerApplicationController(SellerApplicationService service) {
        this.service = service;
    }

    public record RejectRequest(String reason) {
    }

    @PostMapping(value = "/api/seller-applications", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SellerApplicationResponse> apply(@RequestBody SellerApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.apply(request));
    }

    /** The user's latest application, or 204 when they never applied. */
    @GetMapping("/api/seller-applications/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SellerApplicationResponse> mine() {
        return service.mine()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/api/admin/seller-applications")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public PagedResponse<SellerApplicationResponse> list(
            @RequestParam(required = false) SellerApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.adminList(status, page, size);
    }

    @PostMapping("/api/admin/seller-applications/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public SellerApplicationResponse approve(@PathVariable @Positive Long id) {
        return service.approve(id);
    }

    @PostMapping(value = "/api/admin/seller-applications/{id}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public SellerApplicationResponse reject(@PathVariable @Positive Long id,
                                            @RequestBody(required = false) RejectRequest request) {
        return service.reject(id, request == null ? null : request.reason());
    }
}
