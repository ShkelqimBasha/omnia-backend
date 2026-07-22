package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.ProductImageRequest;
import com.omnia.backend.dto.response.ProductImageResponse;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductImage;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.mapper.ProductImageMapper;
import com.omnia.backend.repository.ProductImageRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.UploadedFileRepository;
import com.omnia.backend.service.interfaces.ProductImageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class ProductImageServiceImpl
        implements ProductImageService {

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final UploadedFileRepository uploadedFileRepository;

    public ProductImageServiceImpl(
            ProductRepository productRepository,
            ProductImageRepository imageRepository,
            UploadedFileRepository uploadedFileRepository
    ) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.uploadedFileRepository =
                uploadedFileRepository;
    }

    @Override
    @Transactional
    public ProductImageResponse addImage(
            Long productId,
            ProductImageRequest request
    ) {
        validatePositiveId(
                productId,
                "Product id"
        );

        Product product = productRepository
                .findById(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found"
                        )
                );

        UploadedFile uploadedFile =
                getUploadedFile(
                        request.getUploadedFileId()
                );

        if (imageRepository.existsByUploadedFileId(
                uploadedFile.getId()
        )) {
            throw new IllegalArgumentException(
                    "Uploaded file is already attached "
                            + "to a product image"
            );
        }

        boolean primary =
                Boolean.TRUE.equals(
                        request.getIsPrimary()
                );

        if (primary) {
            clearExistingPrimary(
                    product.getId(),
                    null
            );
        }

        ProductImage image = ProductImage.builder()
                .product(product)
                .uploadedFile(uploadedFile)
                .legacyImageUrl(null)
                .isPrimary(primary)
                .build();

        ProductImage saved =
                imageRepository.saveAndFlush(image);

        return ProductImageMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageResponse> getProductImages(
            Long productId
    ) {
        validatePositiveId(
                productId,
                "Product id"
        );

        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException(
                    "Product not found"
            );
        }

        return imageRepository
                .findByProductIdOrderByIsPrimaryDescIdAsc(
                        productId
                )
                .stream()
                .map(ProductImageMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductImageResponse updateImage(
            Long imageId,
            ProductImageRequest request
    ) {
        validatePositiveId(
                imageId,
                "Image id"
        );

        ProductImage image = imageRepository
                .findById(imageId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Image not found"
                        )
                );

        UploadedFile uploadedFile =
                getUploadedFile(
                        request.getUploadedFileId()
                );

        if (imageRepository
                .existsByUploadedFileIdAndIdNot(
                        uploadedFile.getId(),
                        image.getId()
                )) {
            throw new IllegalArgumentException(
                    "Uploaded file is already attached "
                            + "to another product image"
            );
        }

        boolean primary =
                Boolean.TRUE.equals(
                        request.getIsPrimary()
                );

        if (primary) {
            clearExistingPrimary(
                    image.getProduct().getId(),
                    image.getId()
            );
        }

        image.setUploadedFile(uploadedFile);
        image.setLegacyImageUrl(null);
        image.setIsPrimary(primary);

        ProductImage updated =
                imageRepository.saveAndFlush(image);

        return ProductImageMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteImage(Long imageId) {

        validatePositiveId(
                imageId,
                "Image id"
        );

        ProductImage image = imageRepository
                .findById(imageId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Image not found"
                        )
                );

        /*
         * Delete only the product-image relationship.
         *
         * The uploaded file is intentionally retained.
         * Automatically deleting a physical file inside the same
         * database transaction could leave the database pointing
         * to a missing file if the transaction later rolls back.
         *
         * The owner or an administrator can explicitly delete the
         * detached file through /api/files/{fileId}.
         */
        imageRepository.delete(image);
        imageRepository.flush();
    }

    private UploadedFile getUploadedFile(
            Long uploadedFileId
    ) {
        validatePositiveId(
                uploadedFileId,
                "Uploaded file id"
        );

        return uploadedFileRepository
                .findById(uploadedFileId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Uploaded file not found"
                        )
                );
    }

    private void clearExistingPrimary(
            Long productId,
            Long excludedImageId
    ) {
        imageRepository
                .findPrimaryForUpdate(productId)
                .filter(existing ->
                        excludedImageId == null
                                || !Objects.equals(
                                existing.getId(),
                                excludedImageId
                        )
                )
                .ifPresent(existing -> {
                    existing.setIsPrimary(false);
                    existing.setPrimaryProductId(null);

                    imageRepository.saveAndFlush(
                            existing
                    );
                });
    }

    private void validatePositiveId(
            Long id,
            String fieldName
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
    }
}