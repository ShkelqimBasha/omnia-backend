package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.response.FavoriteResponse;

import java.util.List;

public interface FavoriteService {

    FavoriteResponse addFavorite(Long productId);

    List<FavoriteResponse> getFavorites();

    void removeFavorite(Long productId);
}