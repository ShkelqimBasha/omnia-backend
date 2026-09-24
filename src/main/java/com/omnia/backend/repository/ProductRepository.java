package com.omnia.backend.repository;

import com.omnia.backend.entity.Product;
import com.omnia.backend.enums.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository
        extends JpaRepository<Product, Long>,
        JpaSpecificationExecutor<Product> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(
            @Param("id") Long id
    );

    long countByOrganizationId(
            Long organizationId
    );

    long countByOrganizationIdAndStatus(
            Long organizationId,
            ProductStatus status
    );

    long countByOrganizationIdAndStatusAndStockLessThan(
            Long organizationId,
            ProductStatus status,
            Integer stock
    );

    long countByStatus(
            ProductStatus status
    );

    long countByStatusAndStockLessThan(
            ProductStatus status,
            Integer stock
    );

    long countByOrganizationIdAndStatusAndStockLessThanEqual(
            Long organizationId,
            ProductStatus status,
            Integer stock
    );

    long countByStatusAndStockLessThanEqual(
            ProductStatus status,
            Integer stock
    );}