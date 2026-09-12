package com.omnia.backend.repository;

import com.omnia.backend.entity.User;
import com.omnia.backend.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long>,
        JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    long countByStatus(UserStatus status);

    long countByStatusAndRoleNameIn(
            UserStatus status,
            Collection<String> roleNames
    );

    long countByRoleNameIn(
            Collection<String> roleNames
    );

    @Query("""
        select user
        from User user
        where user.status = :status
          and user.emailVerified = true
          and (
                lower(user.username) like lower(
                    concat('%', :query, '%')
                )
                or lower(user.email) like lower(
                    concat('%', :query, '%')
                )
              )
          and not exists (
                select member.id
                from OrganizationMember member
                where member.organization.id = :organizationId
                  and member.user.id = user.id
              )
        order by user.username asc
        """)
    List<User> searchOrganizationMemberCandidates(
            @Param("organizationId") Long organizationId,
            @Param("status") UserStatus status,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            select case when count(user) > 0
                        then true
                        else false
                   end
            from User user
            where user.avatarFile.id = :avatarFileId
              and user.id <> :userId
            """)
    boolean existsByAvatarFileIdAndUserIdNot(
            @Param("avatarFileId") Long avatarFileId,
            @Param("userId") Long userId
    );
}