package com.omnia.backend.service.impl;

import com.omnia.backend.catalog.ProductImageLimits;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.ProductImageRequest;
import com.omnia.backend.dto.response.ProductImageResponse;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductImage;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.entity.User;
import com.omnia.backend.mapper.ProductImageMapper;
import com.omnia.backend.repository.ProductImageRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.UploadedFileRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.security.service.OrganizationAccessService;
import com.omnia.backend.service.interfaces.ProductImageService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ProductImageServiceImpl
        implements ProductImageService {

    private final ProductRepository productRepository;

    private final ProductImageRepository imageRepository;

    private final UploadedFileRepository
            uploadedFileRepository;

    private final OrganizationAccessService accessService;

    private final CurrentUserService currentUserService;

    public ProductImageServiceImpl(
            ProductRepository productRepository,
            ProductImageRepository imageRepository,
            UploadedFileRepository uploadedFileRepository,
            OrganizationAccessService accessService,
            CurrentUserService currentUserService
    ) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.uploadedFileRepository =
                uploadedFileRepository;
        this.accessService = accessService;
        this.currentUserService = currentUserService;
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

        Product product =
                productRepository
                        .findByIdForUpdate(productId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Product not found"
                                )
                        );

        requireCanManageProduct(product);

        long existingImages =
                imageRepository.countByProductId(productId);
        int maxImages = ProductImageLimits.maxFor(
                product.getCategory() == null
                        ? null
                        : product.getCategory().getKind()
        );
        if (existingImages >= maxImages) {
            throw new IllegalArgumentException(
                    "Të shumtën " + maxImages
                            + " foto për këtë listim"
            );
        }

        UploadedFile uploadedFile =
                getUploadedFile(
                        request.getUploadedFileId()
                );

        requireCanUseUploadedFile(uploadedFile);

        if (imageRepository.existsByUploadedFileId(
                uploadedFile.getId()
        )) {
            throw new IllegalArgumentException(
                    "Uploaded file is already attached "
                            + "to a product image"
            );
        }

        // The first photo of a listing is its cover.
        boolean primary =
                existingImages == 0
                        || Boolean.TRUE.equals(
                        request.getIsPrimary()
                );

        if (primary) {
            clearExistingPrimary(
                    product.getId(),
                    null
            );
        }

        ProductImage image =
                ProductImage.builder()
                        .product(product)
                        .uploadedFile(uploadedFile)
                        .legacyImageUrl(null)
                        .isPrimary(primary)
                        .sortOrder((int) existingImages)
                        .build();

        ProductImage saved =
                imageRepository.saveAndFlush(image);

        renumber(product.getId());

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
                .findByProductIdOrderByIsPrimaryDescSortOrderAscIdAsc(
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

        ProductImage image =
                imageRepository
                        .findById(imageId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Image not found"
                                )
                        );

        requireCanManageProduct(
                image.getProduct()
        );

        UploadedFile uploadedFile =
                getUploadedFile(
                        request.getUploadedFileId()
                );

        requireCanUseUploadedFile(uploadedFile);

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

        renumber(image.getProduct().getId());

        return ProductImageMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteImage(Long imageId) {

        validatePositiveId(
                imageId,
                "Image id"
        );

        ProductImage image =
                imageRepository
                        .findById(imageId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Image not found"
                                )
                        );

        requireCanManageProduct(
                image.getProduct()
        );

        /*
         * Delete only the product-image relationship.
         * The uploaded file remains stored and can be
         * removed separately through /api/files/{fileId}.
         */
        Long productId = image.getProduct().getId();
        boolean wasCover =
                Boolean.TRUE.equals(image.getIsPrimary());

        imageRepository.delete(image);
        imageRepository.flush();

        // Removing the cover promotes the next photo in the gallery.
        if (wasCover) {
            imageRepository
                    .findByProductIdOrderByIsPrimaryDescSortOrderAscIdAsc(
                            productId
                    )
                    .stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setIsPrimary(true);
                        imageRepository.saveAndFlush(next);
                    });
        }

        renumber(productId);
    }

    @Override
    @Transactional
    public List<ProductImageResponse> reorderImages(
            Long productId,
            List<Long> imageIds
    ) {
        validatePositiveId(
                productId,
                "Product id"
        );

        Product product =
                productRepository
                        .findByIdForUpdate(productId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Product not found"
                                )
                        );

        requireCanManageProduct(product);

        List<ProductImage> images =
                imageRepository
                        .findByProductIdOrderByIsPrimaryDescSortOrderAscIdAsc(
                                productId
                        );

        Set<Long> current = new HashSet<>();
        images.forEach(image -> current.add(image.getId()));

        if (imageIds == null
                || imageIds.size() != images.size()
                || !current.equals(new HashSet<>(imageIds))) {
            throw new IllegalArgumentException(
                    "Renditja duhet të përmbajë të gjitha fotot "
                            + "e listimit, secilën një herë"
            );
        }

        applyOrder(product.getId(), images, imageIds);

        return getProductImages(productId);
    }

    @Override
    @Transactional
    public List<ProductImageResponse> setCover(Long imageId) {

        validatePositiveId(
                imageId,
                "Image id"
        );

        ProductImage image =
                imageRepository
                        .findById(imageId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Image not found"
                                )
                        );

        Long productId = image.getProduct().getId();

        productRepository
                .findByIdForUpdate(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found"
                        )
                );

        requireCanManageProduct(image.getProduct());

        List<ProductImage> images =
                imageRepository
                        .findByProductIdOrderByIsPrimaryDescSortOrderAscIdAsc(
                                productId
                        );

        List<Long> order = new ArrayList<>();
        order.add(imageId);
        images.stream()
                .map(ProductImage::getId)
                .filter(id -> !Objects.equals(id, imageId))
                .forEach(order::add);

        applyOrder(productId, images, order);

        return getProductImages(productId);
    }

    /* Positions follow the given ids; the first one becomes the cover. */
    private void applyOrder(
            Long productId,
            List<ProductImage> images,
            List<Long> orderedIds
    ) {
        if (orderedIds.isEmpty()) {
            return;
        }

        Long coverId = orderedIds.get(0);

        clearExistingPrimary(productId, coverId);

        for (ProductImage image : images) {
            image.setSortOrder(orderedIds.indexOf(image.getId()));
            image.setIsPrimary(Objects.equals(image.getId(), coverId));
        }

        imageRepository.saveAllAndFlush(images);
    }

    /* Rewrites positions as 0..n-1 in gallery order (cover first). */
    private void renumber(Long productId) {
        List<ProductImage> images =
                imageRepository
                        .findByProductIdOrderByIsPrimaryDescSortOrderAscIdAsc(
                                productId
                        );

        boolean changed = false;
        for (int position = 0; position < images.size(); position++) {
            ProductImage image = images.get(position);
            if (!Objects.equals(image.getSortOrder(), position)) {
                image.setSortOrder(position);
                changed = true;
            }
        }

        if (changed) {
            imageRepository.saveAllAndFlush(images);
        }
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

    private void requireCanManageProduct(
            Product product
    ) {
        User currentUser =
                currentUserService
                        .findCurrentUser()
                        .orElse(null);

        if (isPlatformAdministrator(currentUser)) {
            return;
        }

        Organization organization =
                product.getOrganization();

        if (organization == null) {
            /*
             * Only the platform administrator can manage
             * images of old products without an organization.
             */
            currentUserService.requirePlatformAdmin();
            return;
        }

        accessService.requireCanUpdateProduct(
                organization.getId(),
                product.getCategory().getId()
        );
    }

    private void requireCanUseUploadedFile(
            UploadedFile uploadedFile
    ) {
        User currentUser =
                currentUserService
                        .findCurrentUser()
                        .orElse(null);

        if (isPlatformAdministrator(currentUser)) {
            return;
        }

        if (currentUser == null) {
            currentUser =
                    currentUserService.requireCurrentUser();
        }

        User fileOwner =
                uploadedFile.getUploadedBy();

        if (fileOwner == null
                || fileOwner.getId() == null
                || !Objects.equals(
                fileOwner.getId(),
                currentUser.getId()
        )) {
            throw new AccessDeniedException(
                    "You can only attach files "
                            + "uploaded by your account"
            );
        }
    }

    private boolean isPlatformAdministrator(
            User currentUser
    ) {
        return currentUserService
                .hasCurrentPlatformAdminAuthority()
                || currentUserService
                .hasPlatformAdminAccess(currentUser);
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