package com.omnia.backend.repository;

import com.omnia.backend.entity.ProductImage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository
        extends JpaRepository<ProductImage, Long> {

    @EntityGraph(
            attributePaths = {
                    "product",
                    "uploadedFile"
            }
    )
    List<ProductImage>
    findByProductIdOrderByIsPrimaryDescIdAsc(
            Long productId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select image
            from ProductImage image
            where image.product.id = :productId
              and image.isPrimary = true
            """)
    Optional<ProductImage> findPrimaryForUpdate(
            @Param("productId") Long productId
    );

    @Query("""
            select case when count(image) > 0
                        then true
                        else false
                   end
            from ProductImage image
            where image.uploadedFile.id = :uploadedFileId
            """)
    boolean existsByUploadedFileId(
            @Param("uploadedFileId") Long uploadedFileId
    );

    @Query("""
            select case when count(image) > 0
                        then true
                        else false
                   end
            from ProductImage image
            where image.uploadedFile.id = :uploadedFileId
              and image.id <> :imageId
            """)
    boolean existsByUploadedFileIdAndIdNot(
            @Param("uploadedFileId") Long uploadedFileId,
            @Param("imageId") Long imageId
    );
}