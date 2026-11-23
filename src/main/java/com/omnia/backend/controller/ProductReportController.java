package com.omnia.backend.controller;

import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.enums.ProductReportReason;
import com.omnia.backend.enums.ProductReportStatus;
import com.omnia.backend.service.impl.ProductReportService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** "Raporto njoftimin" (logged-in users) and the moderation queue (platform admins). */
@RestController
@Validated
public class ProductReportController {

    private final ProductReportService service;

    public ProductReportController(ProductReportService service) {
        this.service = service;
    }

    public record ReportRequest(ProductReportReason reason, String details) {
    }

    public record DecisionRequest(String note) {
    }

    /** Public: the reasons with their Albanian labels. */
    @GetMapping("/api/products/report-reasons")
    public List<ProductReportService.Reason> reasons() {
        return ProductReportService.reasons();
    }

    @PostMapping("/api/products/{productId}/reports")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductReportService.Created> report(@PathVariable @Positive Long productId,
                                                               @RequestBody ReportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Zgjidh arsyen e raportimit");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.report(productId, request.reason(), request.details()));
    }

    @GetMapping("/api/admin/product-reports")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public PagedResponse<ProductReportService.View> queue(
            @RequestParam(required = false) ProductReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.queue(status, page, size);
    }

    @PostMapping("/api/admin/product-reports/{id}/hide")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ProductReportService.View hide(@PathVariable @Positive Long id,
                                          @RequestBody(required = false) DecisionRequest request) {
        return service.hide(id, note(request));
    }

    @PostMapping("/api/admin/product-reports/{id}/dismiss")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ProductReportService.View dismiss(@PathVariable @Positive Long id,
                                             @RequestBody(required = false) DecisionRequest request) {
        return service.dismiss(id, note(request));
    }

    @PostMapping("/api/admin/product-reports/{id}/warn")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ProductReportService.View warn(@PathVariable @Positive Long id,
                                          @RequestBody(required = false) DecisionRequest request) {
        return service.warn(id, note(request));
    }

    @PostMapping("/api/admin/product-reports/{id}/restore")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ProductReportService.View restore(@PathVariable @Positive Long id) {
        return service.restore(id);
    }

    private static String note(DecisionRequest request) {
        return request == null ? null : request.note();
    }
}
