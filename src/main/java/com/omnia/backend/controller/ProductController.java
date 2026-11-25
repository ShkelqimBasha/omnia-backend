package com.omnia.backend.controller;

import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.ProductRequest;
import com.omnia.backend.dto.response.ProductResponse;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.service.interfaces.ProductService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final com.omnia.backend.service.impl.CategoryViews categoryViews;

    public ProductController(ProductService productService,
                             com.omnia.backend.service.impl.CategoryViews categoryViews) {
        this.productService = productService;
        this.categoryViews = categoryViews;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest request
    ) {
        com.omnia.backend.service.impl.AdminAudit.note("Produkti: " + request.getName());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(productService.createProduct(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable Long id
    ) {
        ProductResponse product = productService.getProductById(id);
        categoryViews.recordCurrent(id);
        return ResponseEntity.ok(product);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ProductResponse>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sizes,
            @RequestParam java.util.Map<String, String> allParams
    ) {
        return ResponseEntity.ok(
                productService.getAllProducts(
                        page,
                        size,
                        sortBy,
                        sortDir,
                        keyword,
                        categoryId,
                        organizationId,
                        brand,
                        status,
                        minPrice,
                        maxPrice,
                        com.omnia.backend.catalog.AttributeFilter.fromQuery(allParams),
                        com.omnia.backend.catalog.ProductSizes.parseFilter(sizes),
                        com.omnia.backend.catalog.CatalogFilters.fromQuery(allParams)
                )
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {
        com.omnia.backend.service.impl.AdminAudit.note("Produkti: " + request.getName());
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long id
    ) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}