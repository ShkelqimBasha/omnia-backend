package com.omnia.backend.repository;

import com.omnia.backend.entity.OrderItem;
import com.omnia.backend.enums.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    @Query("""
            select item.productName, sum(item.quantity)
            from OrderItem item
            where item.order.status = :status
            group by item.productName
            order by sum(item.quantity) desc,
                     item.productName asc
            """)
    List<Object[]> findBestSellingProducts(
            @Param("status")
            OrderStatus status,
            Pageable pageable
    );

    @Query("""
            select item.productName, sum(item.quantity)
            from OrderItem item
            where item.order.organization.id = :organizationId
              and item.order.status = :status
            group by item.productName
            order by sum(item.quantity) desc,
                     item.productName asc
            """)
    List<Object[]> findBestSellingProductsByOrganizationId(
            @Param("organizationId")
            Long organizationId,
            @Param("status")
            OrderStatus status,
            Pageable pageable
    );

    @Query("""
            select product.category.name, sum(item.quantity)
            from OrderItem item, Product product
            where product.id = item.productId
              and item.order.status = :status
            group by product.category.name
            order by sum(item.quantity) desc,
                     product.category.name asc
            """)
    List<Object[]> findTopSellingCategories(
            @Param("status")
            OrderStatus status,
            Pageable pageable
    );

    @Query("""
            select product.category.name, sum(item.quantity)
            from OrderItem item, Product product
            where product.id = item.productId
              and item.order.organization.id = :organizationId
              and item.order.status = :status
            group by product.category.name
            order by sum(item.quantity) desc,
                     product.category.name asc
            """)
    List<Object[]> findTopSellingCategoriesByOrganizationId(
            @Param("organizationId")
            Long organizationId,
            @Param("status")
            OrderStatus status,
            Pageable pageable
    );}