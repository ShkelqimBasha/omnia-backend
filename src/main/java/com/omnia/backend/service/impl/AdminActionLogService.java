package com.omnia.backend.service.impl;

import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.entity.AdminActionLog;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.AdminActionLogRepository;
import com.omnia.backend.security.service.CurrentUserService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Writes and searches the admin action log (who changed what, when). */
@Service
public class AdminActionLogService {

    private static final ZoneId TIRANA = ZoneId.of("Europe/Tirane");

    private final AdminActionLogRepository repository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public AdminActionLogService(AdminActionLogRepository repository,
                                 CurrentUserService currentUserService,
                                 Clock clock) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    public record Entry(
            Long id,
            Long actorId,
            String actorLabel,
            String action,
            String httpMethod,
            String route,
            String targetType,
            String targetTypeLabel,
            String targetId,
            String detail,
            int responseStatus,
            OffsetDateTime createdAt
    ) {
    }

    public record Actor(Long id, String label) {
    }

    /** Called after a successful admin request; its own transaction so it never undoes the action. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String method, String route, Map<String, String> pathVariables,
                       String detail, int status) {
        User actor = currentUserService.findCurrentUser().orElse(null);
        repository.save(AdminActionLog.builder()
                .actorId(actor == null ? null : actor.getId())
                .actorLabel(actorLabel(actor))
                .httpMethod(method.toUpperCase(Locale.ROOT))
                .route(route.length() > 200 ? route.substring(0, 200) : route)
                .targetType(AdminActionRules.targetType(route))
                .targetId(AdminActionRules.targetId(pathVariables))
                .detail(AdminActionRules.sanitize(detail))
                .responseStatus(status)
                .createdAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)))
                .build());
    }

    @Transactional(readOnly = true)
    public PagedResponse<Entry> search(Long actorId, String targetType, String query,
                                       LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Data e fillimit duhet të jetë para datës së mbarimit");
        }
        Specification<AdminActionLog> spec = (root, cq, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (actorId != null) {
                where.add(cb.equal(root.get("actorId"), actorId));
            }
            if (targetType != null && !targetType.isBlank()) {
                where.add(cb.equal(root.get("targetType"), targetType.trim().toUpperCase(Locale.ROOT)));
            }
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), utc(from)));
            }
            if (to != null) {
                where.add(cb.lessThan(root.get("createdAt"), utc(to.plusDays(1))));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.get("detail")), like, '\\'),
                        cb.like(cb.lower(root.get("actorLabel")), like, '\\'),
                        cb.like(cb.lower(root.get("route")), like, '\\'),
                        cb.equal(root.get("targetId"), query.trim())));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return PagedResponse.from(repository.findAll(spec,
                        PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)),
                                Sort.by(Sort.Direction.DESC, "id")))
                .map(AdminActionLogService::toEntry));
    }

    /** Everyone who has an entry, for the "Administratori" filter. */
    @Transactional(readOnly = true)
    public List<Actor> actors() {
        return repository.actors().stream()
                .map(row -> new Actor(((Number) row[0]).longValue(), String.valueOf(row[1])))
                .sorted(java.util.Comparator.comparing(Actor::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static Entry toEntry(AdminActionLog log) {
        return new Entry(log.getId(), log.getActorId(), log.getActorLabel(),
                AdminActionRules.label(log.getHttpMethod(), log.getRoute()),
                log.getHttpMethod(), log.getRoute(), log.getTargetType(),
                AdminActionRules.typeLabel(log.getTargetType()), log.getTargetId(), log.getDetail(),
                log.getResponseStatus(), log.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private static LocalDateTime utc(LocalDate tiranaDate) {
        return tiranaDate.atStartOfDay(TIRANA).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    static String actorLabel(User user) {
        if (user == null) {
            return "I panjohur";
        }
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        String handle = user.getUsername() != null ? user.getUsername() : user.getEmail();
        String label = name.isEmpty() ? String.valueOf(handle) : name + " (" + handle + ")";
        return label.length() > 150 ? label.substring(0, 150) : label;
    }
}
