package com.omnia.backend.repository;

import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.enums.OrganizationMemberRole;
import com.omnia.backend.enums.OrganizationMemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OrganizationMemberRepository
        extends JpaRepository<OrganizationMember, Long> {

    Optional<OrganizationMember>
    findByOrganizationIdAndUserId(
            Long organizationId,
            Long userId
    );

    Optional<OrganizationMember>
    findByOrganizationIdAndUserIdAndStatus(
            Long organizationId,
            Long userId,
            OrganizationMemberStatus status
    );

    List<OrganizationMember>
    findAllByUserIdAndStatusOrderByOrganizationNameAsc(
            Long userId,
            OrganizationMemberStatus status
    );

    List<OrganizationMember>
    findAllByOrganizationIdOrderByIdAsc(
            Long organizationId
    );

    Page<OrganizationMember>
    findAllByOrganizationId(
            Long organizationId,
            Pageable pageable
    );

    boolean existsByOrganizationIdAndUserId(
            Long organizationId,
            Long userId
    );

    long countByOrganizationIdAndMembershipRoleAndStatus(
            Long organizationId,
            OrganizationMemberRole membershipRole,
            OrganizationMemberStatus status
    );

    @Query("""
            select distinct member.user.id
            from OrganizationMember member
            where member.status = :status
              and member.membershipRole in :roles
            """)
    Set<Long> findDistinctAdminUserIds(
            @Param("status")
            OrganizationMemberStatus status,

            @Param("roles")
            Collection<OrganizationMemberRole> roles
    );

    @Query("""
            select count(distinct member.user.id)
            from OrganizationMember member
            where member.status = :status
              and member.membershipRole in :roles
            """)
    long countDistinctAdminUsers(
            @Param("status")
            OrganizationMemberStatus status,

            @Param("roles")
            Collection<OrganizationMemberRole> roles
    );
}