package com.omnia.backend.service.impl;

import com.omnia.backend.security.service.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Remembers which categories a signed-in user looks at (one aggregate row per user and category),
 * so a push campaign can target "people who viewed Makina". Anonymous visits record nothing.
 */
@Service
public class CategoryViews {

    private static final Logger LOG = LoggerFactory.getLogger(CategoryViews.class);
    private final JdbcTemplate jdbc;
    private final CurrentUserService current;

    public CategoryViews(JdbcTemplate jdbc, CurrentUserService current) {
        this.jdbc = jdbc;
        this.current = current;
    }

    /** Records a view of an active product for the signed-in user, if any. Never fails the request. */
    public void recordCurrent(long productId) {
        try {
            current.findCurrentUser().ifPresent(u -> record(u.getId(), productId));
        } catch (RuntimeException e) {
            LOG.debug("Category view not recorded ({})", e.getClass().getSimpleName());
        }
    }

    /** Repeat views within a minute (reloads, prefetches) do not raise the count. */
    void record(long userId, long productId) {
        jdbc.update("INSERT INTO user_category_views(user_id,category_id,view_count,last_viewed_at)"
                + " SELECT ?,p.category_id,1,UTC_TIMESTAMP(6) FROM products p WHERE p.id=? AND p.status='ACTIVE' AND p.category_id IS NOT NULL"
                + " ON DUPLICATE KEY UPDATE view_count=IF(last_viewed_at<DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE),view_count+1,view_count),"
                + " last_viewed_at=UTC_TIMESTAMP(6)", userId, productId);
    }
}
