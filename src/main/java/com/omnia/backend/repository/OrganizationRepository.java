package com.omnia.backend.repository;

import com.omnia.backend.entity.Organization;
import com.omnia.backend.enums.OrganizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository
        extends JpaRepository<Organization, Long> {

    boolean existsByNameIgnoreCaseAndIdNot(String name,Long id);
    boolean existsBySlugIgnoreCaseAndIdNot(String slug,Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Organization o where o.id = :id")
    Optional<Organization> findForEditor(@org.springframework.data.repository.query.Param("id") Long id);

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
