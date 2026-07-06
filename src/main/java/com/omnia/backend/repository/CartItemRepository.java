package com.omnia.backend.repository;

import com.omnia.backend.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartId(Long cartId);

    Optional<CartItem> findByCartIdAndProductIdAndVariantId(
            Long cartId,
            Long productId,
            Long variantId
    );

    void deleteByCartId(Long cartId);
}
