package com.omnia.backend.service.impl;

import com.omnia.backend.catalog.SavedSearchFilters;
import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.entity.SavedSearch;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.SavedSearchRepository;
import com.omnia.backend.security.service.CurrentUserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** A buyer's saved searches. Every call is scoped to the signed-in user. */
@Service
public class SavedSearchService {

    public static final int MAX_PER_USER = 20;

    public record CreateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotEmpty Map<String, String> filters,
            Boolean alertsEnabled) {
    }

    public record UpdateRequest(@Size(max = 120) String name, Boolean alertsEnabled) {
    }

    public record Response(Long id, String name, Map<String, String> filters, boolean alertsEnabled,
                           LocalDateTime createdAt, LocalDateTime lastNotifiedAt) {
        static Response of(SavedSearch s) {
            return new Response(s.getId(), s.getName(), s.getFilters(), s.isAlertsEnabled(),
                    s.getCreatedAt(), s.getLastNotifiedAt());
        }
    }

    private final SavedSearchRepository repository;
    private final CurrentUserService currentUser;
    private final JdbcTemplate jdbc;

    public SavedSearchService(SavedSearchRepository repository, CurrentUserService currentUser, JdbcTemplate jdbc) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Response> mine() {
        return repository.findByUserIdOrderByIdDesc(currentUser.requireCurrentUser().getId())
                .stream().map(Response::of).toList();
    }

    @Transactional
    public Response create(CreateRequest request) {
        User user = currentUser.requireCurrentUser();
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > 120) {
            throw new IllegalArgumentException("Name must contain between 1 and 120 characters");
        }
        Map<String, String> filters = SavedSearchFilters.normalize(request.filters());
        String hash = SavedSearchFilters.hash(filters);
        if (repository.existsByUserIdAndFiltersHash(user.getId(), hash)) {
            throw new ConflictException("This search is already saved");
        }
        if (repository.countByUserId(user.getId()) >= MAX_PER_USER) {
            throw new ConflictException("You can save up to " + MAX_PER_USER + " searches");
        }
        // Only products listed after saving are "new" for the alerts.
        Long newest = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM products", Long.class);
        SavedSearch saved = repository.saveAndFlush(SavedSearch.builder()
                .user(user)
                .name(name)
                .filters(filters)
                .filtersHash(hash)
                .alertsEnabled(request.alertsEnabled() == null || request.alertsEnabled())
                .lastSeenProductId(newest == null ? 0L : newest)
                .build());
        return Response.of(repository.findById(saved.getId()).orElse(saved));
    }

    @Transactional
    public Response update(Long id, UpdateRequest request) {
        SavedSearch search = own(id);
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) throw new IllegalArgumentException("Name must not be blank");
            search.setName(name);
        }
        if (request.alertsEnabled() != null) {
            if (request.alertsEnabled() && !search.isAlertsEnabled()) {
                // Turning alerts back on does not report everything listed while they were off.
                Long newest = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM products", Long.class);
                search.setLastSeenProductId(Math.max(search.getLastSeenProductId(), newest == null ? 0L : newest));
            }
            search.setAlertsEnabled(request.alertsEnabled());
        }
        return Response.of(repository.save(search));
    }

    @Transactional
    public void delete(Long id) {
        SavedSearch search = own(id);
        // Its notices would point to nothing; remove them with it.
        jdbc.update("DELETE FROM notification_inbox WHERE target_type='SEARCH' AND target_id=? AND recipient_id=?",
                search.getId(), search.getUser().getId());
        repository.delete(search);
    }

    private SavedSearch own(Long id) {
        return repository.findByIdAndUserId(id, currentUser.requireCurrentUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Saved search not found"));
    }
}
