package com.omnia.backend.service.impl;

import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.ProductImageRepository;
import com.omnia.backend.service.interfaces.PublicCatalogMediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicCatalogMediaServiceImpl
        implements PublicCatalogMediaService {

    private final ProductImageRepository productImageRepository;

    private final OrganizationRepository organizationRepository;

    public PublicCatalogMediaServiceImpl(
            ProductImageRepository productImageRepository,
            OrganizationRepository organizationRepository
    ) {
        this.productImageRepository = productImageRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPublicCatalogFile(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return false;
        }

        return productImageRepository
                .existsForActiveProductByUploadedFileId(fileId)
                || organizationRepository
                .existsActiveByLogoFileId(fileId);
    }
}
