package com.omnia.backend.repository;

import com.omnia.backend.entity.Organization;
import com.omnia.backend.enums.OrganizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository
        extends JpaRepository<Organization, Long> {

    Optional<Organization> findBySlugIgnoreCase(
            String slug
    );

    boolean existsBySlugIgnoreCase(
            String slug
    );

    List<Organization> findAllByStatusOrderByNameAsc(
            OrganizationStatus status
    );
}