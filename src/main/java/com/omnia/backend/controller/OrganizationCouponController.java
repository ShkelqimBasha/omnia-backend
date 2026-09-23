package com.omnia.backend.controller;

import com.omnia.backend.dto.request.CouponRequest;
import com.omnia.backend.dto.response.CouponResponse;
import com.omnia.backend.service.interfaces.CouponService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/organizations/{organizationId}/coupons"
)
@Validated
public class OrganizationCouponController {

    private final CouponService couponService;

    public OrganizationCouponController(
            CouponService couponService
    ) {
        this.couponService = couponService;
    }

    @GetMapping
    public ResponseEntity<List<CouponResponse>>
    getCoupons(
            @PathVariable
            @Positive
            Long organizationId
    ) {
        return ResponseEntity.ok(
                couponService
                        .getCouponsForOrganization(
                                organizationId
                        )
        );
    }

    @PostMapping
    public ResponseEntity<CouponResponse>
    createCoupon(
            @PathVariable
            @Positive
            Long organizationId,
            @Valid
            @RequestBody
            CouponRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        couponService
                                .createCouponForOrganization(
                                        organizationId,
                                        request
                                )
                );
    }

    @DeleteMapping("/{couponId}")
    public ResponseEntity<Void>
    deleteCoupon(
            @PathVariable
            @Positive
            Long organizationId,
            @PathVariable
            @Positive
            Long couponId
    ) {
        couponService
                .deleteCouponForOrganization(
                        organizationId,
                        couponId
                );

        return ResponseEntity.noContent().build();
    }
}