package com.omnia.backend.service.impl;

import com.omnia.backend.catalog.SavedSearchFilters;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.SavedSearch;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.SavedSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Checks saved searches with alerts on and writes one in-app notice (and push, through the
 * push outbox) per search when products listed since the last check match its filters.
 */
@Component
public class SavedSearchAlertJob {

    private static final Logger LOG = LoggerFactory.getLogger(SavedSearchAlertJob.class);
    private static final int BATCH = 200;

    private final SavedSearchRepository searches;
    private final ProductRepository products;
    private final NotificationWriter notifications;
    private final TransactionTemplate tx;
    private final boolean enabled;

    public SavedSearchAlertJob(SavedSearchRepository searches, ProductRepository products,
                               NotificationWriter notifications, PlatformTransactionManager transactions,
                               @Value("${omnia.saved-search.alerts-enabled:true}") boolean enabled) {
        this.searches = searches;
        this.products = products;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(transactions);
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${omnia.saved-search.poll-ms:900000}", initialDelayString = "${omnia.saved-search.initial-delay-ms:120000}")
    public void scheduled() {
        if (enabled) runOnce();
    }

    /** Returns the number of notices written. */
    public int runOnce() {
        int written = 0;
        long after = 0;
        while (true) {
            List<Long> ids = searches.findAlertIdsAfter(after, PageRequest.of(0, BATCH));
            for (Long id : ids) {
                try {
                    Boolean sent = tx.execute(status -> check(id));
                    if (Boolean.TRUE.equals(sent)) written++;
                } catch (RuntimeException e) {
                    // One broken search (e.g. a filter key that no longer exists) must not stop the others.
                    LOG.warn("Saved search {} skipped ({})", id, e.getClass().getSimpleName());
                }
            }
            if (ids.size() < BATCH) return written;
            after = ids.get(ids.size() - 1);
        }
    }

    private boolean check(Long id) {
        SavedSearch search = searches.findById(id).orElse(null);
        if (search == null || !search.isAlertsEnabled()) return false;
        Page<Product> matches = products.findAll(
                SavedSearchFilters.newMatches(search.getFilters(), search.getLastSeenProductId()),
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id")));
        if (matches.isEmpty()) return false;
        long newest = matches.getContent().get(0).getId();
        int count = (int) Math.min(Integer.MAX_VALUE, matches.getTotalElements());
        search.setLastSeenProductId(newest);
        boolean sent = notifications.savedSearch(search.getUser().getId(), search.getId(), search.getName(), newest, count);
        if (sent) search.setLastNotifiedAt(LocalDateTime.now());
        searches.save(search);
        return sent;
    }
}
