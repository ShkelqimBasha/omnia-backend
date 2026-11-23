package com.omnia.backend.service.impl;

import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.OrganizationVerificationRepository;
import com.omnia.backend.repository.ProductImageRepository;
import com.omnia.backend.service.interfaces.PublicCatalogMediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicCatalogMediaServiceImpl
        implements PublicCatalogMediaService {

    private final ProductImageRepository productImageRepository;

    private final OrganizationRepository organizationRepository;

    private final OrganizationVerificationRepository verificationRepository;

    public PublicCatalogMediaServiceImpl(
            ProductImageRepository productImageRepository,
            OrganizationRepository organizationRepository,
            OrganizationVerificationRepository verificationRepository
    ) {
        this.productImageRepository = productImageRepository;
        this.organizationRepository = organizationRepository;
        this.verificationRepository = verificationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPublicCatalogFile(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return false;
        }

        if (isPrivateDocument(fileId)) {
            return false;
        }

        return productImageRepository
                .existsForActiveProductByUploadedFileId(fileId)
                || organizationRepository
                .existsActiveByLogoFileId(fileId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPrivateDocument(Long fileId) {
        return fileId != null
                && fileId > 0
                && verificationRepository.existsByDocumentFileId(fileId);
    }
}
