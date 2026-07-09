package com.omnia.backend.controller;

import com.omnia.backend.dto.request.CouponRequest;
import com.omnia.backend.dto.response.CouponResponse;
import com.omnia.backend.service.interfaces.CouponService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping
    public ResponseEntity<CouponResponse> createCoupon(
            @Valid @RequestBody CouponRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(couponService.createCoupon(request));
    }

    @GetMapping
    public ResponseEntity<List<CouponResponse>> getAllCoupons() {
        return ResponseEntity.ok(couponService.getAllCoupons());
    }

    @GetMapping("/{code}")
    public ResponseEntity<CouponResponse> getCouponByCode(
            @PathVariable String code
    ) {
        return ResponseEntity.ok(couponService.getCouponByCode(code));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCoupon(
            @PathVariable Long id
    ) {
        couponService.deleteCoupon(id);
        return ResponseEntity.noContent().build();
    }
}