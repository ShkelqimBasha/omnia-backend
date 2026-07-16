package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.response.FavoriteResponse;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Favorite;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.repository.FavoriteRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceImplTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FavoriteServiceImpl favoriteService;

    private User user;
    private Category category;
    private Product product;
    private Favorite favorite;

    @BeforeEach
    void setUp() {

        user = User.builder()
                .id(1L)
                .username("shkelqim")
                .email("shkelqim@example.com")
                .build();

        category = Category.builder()
                .id(2L)
                .name("Electronics")
                .build();

        product = Product.builder()
                .id(10L)
                .name("Samsung Galaxy S24")
                .brand("Samsung")
                .price(new BigDecimal("1200.00"))
                .discountPrice(new BigDecimal("1099.00"))
                .category(category)
                .status(ProductStatus.ACTIVE)
                .build();

        favorite = Favorite.builder()
                .id(100L)
                .user(user)
                .product(product)
                .createdAt(
                        LocalDateTime.of(
                                2026,
                                7,
                                16,
                                19,
                                30
                        )
                )
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "shkelqim@example.com",
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addFavorite_shouldAddFavoriteSuccessfully() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(favoriteRepository
                .existsByUserIdAndProductId(1L, 10L))
                .thenReturn(false);

        when(favoriteRepository.save(any(Favorite.class)))
                .thenReturn(favorite);

        FavoriteResponse result =
                favoriteService.addFavorite(10L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(10L, result.getProductId());
        assertEquals(
                "Samsung Galaxy S24",
                result.getProductName()
        );
        assertEquals("Samsung", result.getBrand());
        assertEquals(
                new BigDecimal("1200.00"),
                result.getPrice()
        );
        assertEquals(
                new BigDecimal("1099.00"),
                result.getDiscountPrice()
        );
        assertEquals(
                "Electronics",
                result.getCategory()
        );
        assertEquals("ACTIVE", result.getStatus());

        ArgumentCaptor<Favorite> favoriteCaptor =
                ArgumentCaptor.forClass(Favorite.class);

        verify(favoriteRepository)
                .save(favoriteCaptor.capture());

        Favorite savedFavorite =
                favoriteCaptor.getValue();

        assertEquals(user, savedFavorite.getUser());
        assertEquals(product, savedFavorite.getProduct());
    }

    @Test
    void addFavorite_shouldThrowWhenProductDoesNotExist() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        when(productRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> favoriteService.addFavorite(99L)
                );

        assertEquals(
                "Product not found",
                exception.getMessage()
        );

        verify(favoriteRepository, never())
                .save(any(Favorite.class));
    }

    @Test
    void addFavorite_shouldThrowWhenProductAlreadyExists() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));

        when(favoriteRepository
                .existsByUserIdAndProductId(1L, 10L))
                .thenReturn(true);

        ResourceAlreadyExistsException exception =
                assertThrows(
                        ResourceAlreadyExistsException.class,
                        () -> favoriteService.addFavorite(10L)
                );

        assertEquals(
                "Product is already in favorites",
                exception.getMessage()
        );

        verify(favoriteRepository, never())
                .save(any(Favorite.class));
    }

    @Test
    void getFavorites_shouldReturnFavorites() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        when(favoriteRepository.findByUserId(1L))
                .thenReturn(List.of(favorite));

        List<FavoriteResponse> result =
                favoriteService.getFavorites();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(
                "Samsung Galaxy S24",
                result.getFirst().getProductName()
        );
        assertEquals(
                "Electronics",
                result.getFirst().getCategory()
        );

        verify(favoriteRepository)
                .findByUserId(1L);
    }

    @Test
    void getFavorites_shouldReturnEmptyList() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        when(favoriteRepository.findByUserId(1L))
                .thenReturn(List.of());

        List<FavoriteResponse> result =
                favoriteService.getFavorites();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void removeFavorite_shouldRemoveFavoriteSuccessfully() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        when(favoriteRepository
                .findByUserIdAndProductId(1L, 10L))
                .thenReturn(Optional.of(favorite));

        favoriteService.removeFavorite(10L);

        verify(favoriteRepository)
                .delete(favorite);
    }

    @Test
    void removeFavorite_shouldThrowWhenFavoriteDoesNotExist() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        when(favoriteRepository
                .findByUserIdAndProductId(1L, 99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> favoriteService.removeFavorite(99L)
                );

        assertEquals(
                "Favorite not found",
                exception.getMessage()
        );

        verify(favoriteRepository, never())
                .delete(any(Favorite.class));
    }

    @Test
    void getCurrentUser_shouldFallbackToUsername() {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "shkelqim",
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        when(userRepository.findByEmail("shkelqim"))
                .thenReturn(Optional.empty());

        when(userRepository.findByUsername("shkelqim"))
                .thenReturn(Optional.of(user));

        when(favoriteRepository.findByUserId(1L))
                .thenReturn(List.of(favorite));

        List<FavoriteResponse> result =
                favoriteService.getFavorites();

        assertEquals(1, result.size());

        verify(userRepository)
                .findByUsername("shkelqim");
    }

    @Test
    void getCurrentUser_shouldThrowWhenUserDoesNotExist() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.empty());

        when(userRepository.findByUsername(
                "shkelqim@example.com"
        )).thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> favoriteService.getFavorites()
                );

        assertEquals(
                "User not found",
                exception.getMessage()
        );

        verifyNoInteractions(
                favoriteRepository,
                productRepository
        );
    }
}