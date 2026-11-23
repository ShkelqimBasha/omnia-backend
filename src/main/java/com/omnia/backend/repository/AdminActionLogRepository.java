package com.omnia.backend.repository;

import com.omnia.backend.entity.AdminActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AdminActionLogRepository
        extends JpaRepository<AdminActionLog, Long>, JpaSpecificationExecutor<AdminActionLog> {

    /** [actorId, latest label] for every admin with at least one entry. */
    @Query("select l.actorId, max(l.actorLabel) from AdminActionLog l "
            + "where l.actorId is not null group by l.actorId")
    List<Object[]> actors();
}
