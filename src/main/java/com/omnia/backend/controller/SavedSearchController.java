package com.omnia.backend.controller;

import com.omnia.backend.service.impl.SavedSearchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/saved-searches")
@PreAuthorize("isAuthenticated()")
public class SavedSearchController {

    private final SavedSearchService service;

    public SavedSearchController(SavedSearchService service) {
        this.service = service;
    }

    @GetMapping
    public List<SavedSearchService.Response> mine() {
        return service.mine();
    }

    @PostMapping
    public ResponseEntity<SavedSearchService.Response> create(
            @Valid @RequestBody SavedSearchService.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/{id}")
    public SavedSearchService.Response update(@PathVariable Long id,
                                              @Valid @RequestBody SavedSearchService.UpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
