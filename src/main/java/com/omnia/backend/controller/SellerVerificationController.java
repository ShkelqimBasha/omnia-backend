package com.omnia.backend.controller;

import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.enums.VerificationStatus;
import com.omnia.backend.service.impl.SellerVerificationService;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/** "Shitës i verifikuar": company side (submit, status) and platform admin side (review). */
@RestController
@Validated
public class SellerVerificationController {

    private final SellerVerificationService service;

    public SellerVerificationController(SellerVerificationService service) {
        this.service = service;
    }

    public record DecisionRequest(String reason) {
    }

    @PostMapping(value = "/api/organizations/{organizationId}/verification",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SellerVerificationService.View> submit(
            @PathVariable @Positive Long organizationId,
            @RequestParam("nipt") String nipt,
            @RequestPart("document") MultipartFile document
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(organizationId, nipt, document));
    }

    @GetMapping("/api/organizations/{organizationId}/verification")
    @PreAuthorize("isAuthenticated()")
    public SellerVerificationService.CompanyStatus status(@PathVariable @Positive Long organizationId) {
        return service.companyStatus(organizationId);
    }

    @GetMapping("/api/admin/verifications")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public PagedResponse<SellerVerificationService.View> list(
            @RequestParam(required = false) VerificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.adminList(status, page, size);
    }

    @PostMapping("/api/admin/verifications/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public SellerVerificationService.View approve(@PathVariable @Positive Long id) {
        return service.approve(id);
    }

    @PostMapping(value = "/api/admin/verifications/{id}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public SellerVerificationService.View reject(@PathVariable @Positive Long id,
                                                 @RequestBody DecisionRequest request) {
        return service.reject(id, request == null ? null : request.reason());
    }

    @PostMapping(value = "/api/admin/organizations/{organizationId}/verification/revoke",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public SellerVerificationService.CompanyStatus revoke(@PathVariable @Positive Long organizationId,
                                                          @RequestBody DecisionRequest request) {
        return service.revoke(organizationId, request == null ? null : request.reason());
    }

    /** Always an attachment with nosniff, never rendered inline: the file came from a company. */
    @GetMapping("/api/admin/verifications/{id}/document")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Resource> document(@PathVariable @Positive Long id) {
        SellerVerificationService.Document document = service.document(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .contentLength(document.size())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .body(document.resource());
    }
}
