package com.omnia.backend.controller;

import com.omnia.backend.service.impl.ProductActivityCounter;
import com.omnia.backend.service.impl.ProductStatisticsService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class ProductStatisticsController {

    private final ProductStatisticsService statistics;

    private final ProductActivityCounter counter;

    public ProductStatisticsController(ProductStatisticsService statistics,
                                       ProductActivityCounter counter) {
        this.statistics = statistics;
        this.counter = counter;
    }

    /**
     * Counts one view of a listing. Called by the product page (web, Android)
     * once per visit; open to guests and answered without touching the database.
     */
    @PostMapping("/api/products/{productId}/view")
    public ResponseEntity<Void> view(@PathVariable @Positive Long productId) {
        counter.record(productId, ProductActivityCounter.Metric.VIEW);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/organizations/{organizationId}/statistics/products")
    public ProductStatisticsService.OrganizationProducts products(
            @PathVariable @Positive Long organizationId,
            @RequestParam(defaultValue = "7") int days) {
        return statistics.forOrganization(organizationId, days);
    }

    @GetMapping("/api/organizations/{organizationId}/statistics/products/{productId}")
    public ProductStatisticsService.ProductDetail product(
            @PathVariable @Positive Long organizationId,
            @PathVariable @Positive Long productId,
            @RequestParam(defaultValue = "7") int days) {
        return statistics.forProduct(organizationId, productId, days);
    }
}
