package com.omnia.backend.controller;

import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.ProductInquiryRequest;
import com.omnia.backend.dto.response.ProductInquiryResponse;
import com.omnia.backend.service.impl.ProductInquiryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductInquiryController {

    private final ProductInquiryService inquiryService;

    public ProductInquiryController(ProductInquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    /** "Kontakto" / "Kërko vizitë" on a car or property listing. */
    @PostMapping("/api/products/{productId}/inquiries")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductInquiryResponse> create(
            @PathVariable Long productId,
            @Valid @RequestBody ProductInquiryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inquiryService.create(productId, request));
    }

    @GetMapping("/api/inquiries/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PagedResponse<ProductInquiryResponse>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(inquiryService.mine(page, size));
    }

    @GetMapping("/api/organizations/{organizationId}/inquiries")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PagedResponse<ProductInquiryResponse>> forOrganization(
            @PathVariable Long organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(inquiryService.forOrganization(organizationId, page, size));
    }
}
