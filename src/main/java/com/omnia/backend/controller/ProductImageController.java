package com.omnia.backend.controller;

import com.omnia.backend.dto.request.ProductImageRequest;
import com.omnia.backend.dto.response.ProductImageResponse;
import com.omnia.backend.service.interfaces.ProductImageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/product-images")
@Validated
public class ProductImageController {

    private final ProductImageService productImageService;

    public ProductImageController(
            ProductImageService productImageService
    ) {
        this.productImageService =
                productImageService;
    }

    @PostMapping("/product/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductImageResponse> addImage(
            @PathVariable
            @Positive(message = "Product id must be positive")
            Long productId,

            @Valid
            @RequestBody
            ProductImageRequest request
    ) {
        ProductImageResponse response =
                productImageService.addImage(
                        productId,
                        request
                );

        URI location = URI.create(
                "/api/product-images/"
                        + response.getId()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<ProductImageResponse>>
    getProductImages(
            @PathVariable
            @Positive(message = "Product id must be positive")
            Long productId
    ) {
        return ResponseEntity.ok(
                productImageService
                        .getProductImages(productId)
        );
    }

    @PutMapping("/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductImageResponse> updateImage(
            @PathVariable
            @Positive(message = "Image id must be positive")
            Long imageId,

            @Valid
            @RequestBody
            ProductImageRequest request
    ) {
        return ResponseEntity.ok(
                productImageService.updateImage(
                        imageId,
                        request
                )
        );
    }

    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteImage(
            @PathVariable
            @Positive(message = "Image id must be positive")
            Long imageId
    ) {
        productImageService.deleteImage(imageId);

        return ResponseEntity.noContent().build();
    }
}