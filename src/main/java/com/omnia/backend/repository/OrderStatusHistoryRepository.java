package com.omnia.backend.repository;

import com.omnia.backend.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderStatusHistoryRepository
        extends JpaRepository<
        OrderStatusHistory,
        Long
        > {

    @Query("""
            SELECT history
            FROM OrderStatusHistory history
            LEFT JOIN FETCH history.changedByUser
            WHERE history.order.id = :orderId
            ORDER BY history.changedAt ASC, history.id ASC
            """)
    List<OrderStatusHistory> findAllForOrder(
            @Param("orderId") Long orderId
    );
}