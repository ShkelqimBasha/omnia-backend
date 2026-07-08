package com.omnia.backend.controller;

import com.omnia.backend.dto.request.ProductImageRequest;
import com.omnia.backend.dto.response.ProductImageResponse;
import com.omnia.backend.service.interfaces.ProductImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/product-images")
public class ProductImageController {

    private final ProductImageService productImageService;

    public ProductImageController(ProductImageService productImageService) {
        this.productImageService = productImageService;
    }

    @PostMapping("/product/{productId}")
    public ResponseEntity<ProductImageResponse> addImage(
            @PathVariable Long productId,
            @Valid @RequestBody ProductImageRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(productImageService.addImage(productId, request));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<ProductImageResponse>> getProductImages(
            @PathVariable Long productId
    ) {
        return ResponseEntity.ok(
                productImageService.getProductImages(productId)
        );
    }

    @PutMapping("/{imageId}")
    public ResponseEntity<ProductImageResponse> updateImage(
            @PathVariable Long imageId,
            @Valid @RequestBody ProductImageRequest request
    ) {
        return ResponseEntity.ok(
                productImageService.updateImage(imageId, request)
        );
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long imageId
    ) {
        productImageService.deleteImage(imageId);
        return ResponseEntity.noContent().build();
    }
}