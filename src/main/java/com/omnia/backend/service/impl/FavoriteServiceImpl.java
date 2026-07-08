package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.response.FavoriteResponse;
import com.omnia.backend.entity.Favorite;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.User;
import com.omnia.backend.mapper.FavoriteMapper;
import com.omnia.backend.repository.FavoriteRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.interfaces.FavoriteService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public FavoriteServiceImpl(
            FavoriteRepository favoriteRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.favoriteRepository = favoriteRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public FavoriteResponse addFavorite(Long productId) {

        User user = getCurrentUser();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (favoriteRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new ResourceAlreadyExistsException("Product is already in favorites");
        }

        Favorite favorite = Favorite.builder()
                .user(user)
                .product(product)
                .build();

        Favorite saved = favoriteRepository.save(favorite);

        return FavoriteMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FavoriteResponse> getFavorites() {

        User user = getCurrentUser();

        return favoriteRepository.findByUserId(user.getId())
                .stream()
                .map(FavoriteMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void removeFavorite(Long productId) {

        User user = getCurrentUser();

        Favorite favorite = favoriteRepository
                .findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Favorite not found"));

        favoriteRepository.delete(favorite);
    }

    private User getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String usernameOrEmail = authentication.getName();

        return userRepository.findByEmail(usernameOrEmail)
                .or(() -> userRepository.findByUsername(usernameOrEmail))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}