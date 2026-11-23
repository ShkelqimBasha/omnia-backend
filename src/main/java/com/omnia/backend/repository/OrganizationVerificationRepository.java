package com.omnia.backend.repository;

import com.omnia.backend.entity.OrganizationVerification;
import com.omnia.backend.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationVerificationRepository extends JpaRepository<OrganizationVerification, Long> {

    Optional<OrganizationVerification> findFirstByOrganizationIdOrderByIdDesc(Long organizationId);

    boolean existsByOrganizationIdAndStatus(Long organizationId, VerificationStatus status);

    boolean existsByDocumentFileId(Long documentFileId);

    @EntityGraph(attributePaths = {"organization", "submittedBy", "reviewedBy", "documentFile"})
    Page<OrganizationVerification> findByStatusOrderByIdDesc(VerificationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"organization", "submittedBy", "reviewedBy", "documentFile"})
    Page<OrganizationVerification> findAllByOrderByIdDesc(Pageable pageable);
}
