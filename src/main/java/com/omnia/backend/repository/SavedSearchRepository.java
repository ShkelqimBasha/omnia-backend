package com.omnia.backend.repository;

import com.omnia.backend.entity.SavedSearch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {

    List<SavedSearch> findByUserIdOrderByIdDesc(Long userId);

    Optional<SavedSearch> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    boolean existsByUserIdAndFiltersHash(Long userId, String filtersHash);

    /** Ids of searches with alerts on whose owner is an active account, in id order (keyset). */
    @Query("""
            select s.id from SavedSearch s
            where s.alertsEnabled = true and s.id > :after
              and s.user.status = com.omnia.backend.enums.UserStatus.ACTIVE
              and s.user.deletedAt is null
            order by s.id
            """)
    List<Long> findAlertIdsAfter(@Param("after") Long after, Pageable page);
}
