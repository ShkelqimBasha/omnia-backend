package com.omnia.backend.repository;

import com.omnia.backend.entity.CheckoutGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckoutGroupRepository
        extends JpaRepository<CheckoutGroup, Long> {

    Optional<CheckoutGroup> findByCheckoutReference(
            String checkoutReference
    );

    Optional<CheckoutGroup> findByIdAndUserId(
            Long checkoutGroupId,
            Long userId
    );
}