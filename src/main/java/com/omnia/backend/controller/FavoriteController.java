package com.omnia.backend.controller;

import com.omnia.backend.dto.response.FavoriteResponse;
import com.omnia.backend.service.interfaces.FavoriteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/{productId}")
    public ResponseEntity<FavoriteResponse> addFavorite(
            @PathVariable Long productId
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(favoriteService.addFavorite(productId));
    }

    @GetMapping
    public ResponseEntity<List<FavoriteResponse>> getFavorites() {
        return ResponseEntity.ok(favoriteService.getFavorites());
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeFavorite(
            @PathVariable Long productId
    ) {
        favoriteService.removeFavorite(productId);
        return ResponseEntity.noContent().build();
    }
}