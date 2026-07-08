package com.omnia.backend.repository;

import com.omnia.backend.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductId(Long productId);

    List<ProductImage> findByProductIdOrderByIsPrimaryDescIdAsc(Long productId);
}