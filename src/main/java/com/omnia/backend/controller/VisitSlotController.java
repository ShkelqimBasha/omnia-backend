package com.omnia.backend.controller;

import com.omnia.backend.dto.request.VisitSlotRequest;
import com.omnia.backend.dto.response.VisitSlotResponse;
import com.omnia.backend.service.impl.VisitSlotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Visit times for property listings ("Kërko vizitë"). */
@RestController
public class VisitSlotController {

    private final VisitSlotService slotService;

    public VisitSlotController(VisitSlotService slotService) {
        this.slotService = slotService;
    }

    /** Public: the free upcoming times a buyer can pick. */
    @GetMapping("/api/products/{productId}/visit-slots")
    public ResponseEntity<List<VisitSlotResponse>> available(@PathVariable Long productId) {
        return ResponseEntity.ok(slotService.available(productId));
    }

    /** Seller: all upcoming times, with bookings. */
    @GetMapping("/api/manage/products/{productId}/visit-slots")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<VisitSlotResponse>> upcoming(@PathVariable Long productId) {
        return ResponseEntity.ok(slotService.upcoming(productId));
    }

    @PostMapping("/api/manage/products/{productId}/visit-slots")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<VisitSlotResponse> add(@PathVariable Long productId,
                                                 @Valid @RequestBody VisitSlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(slotService.add(productId, request.getStartsAt()));
    }

    @DeleteMapping("/api/manage/products/{productId}/visit-slots/{slotId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long productId, @PathVariable Long slotId) {
        slotService.delete(productId, slotId);
        return ResponseEntity.noContent().build();
    }
}
