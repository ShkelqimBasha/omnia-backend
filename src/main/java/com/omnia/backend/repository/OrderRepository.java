package com.omnia.backend.repository;

import com.omnia.backend.entity.Order;
import com.omnia.backend.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderRepository
        extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    List<Order> findAllByOrderByCreatedAtDesc();

    List<Order> findAllByOrganizationIdOrderByCreatedAtDesc(
            Long organizationId
    );

    long countByOrganizationId(
            Long organizationId
    );

    long countByOrganizationIdAndStatusNotIn(
            Long organizationId,
            Collection<OrderStatus> statuses
    );

    long countByStatusNotIn(
            Collection<OrderStatus> statuses
    );
}