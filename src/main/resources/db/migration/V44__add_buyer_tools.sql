SET NAMES utf8mb4;

/*
 * Buyer tools.
 *
 * 1. saved_searches: a buyer's saved listing filters ("Golf, nën 8.000 €, Tiranë").
 *    A scheduled job notifies the owner about ACTIVE products with an id above
 *    last_seen_product_id that match the filters.
 * 2. notification_inbox.previous_amount: the old price of a PRICE_DROP notice.
 * 3. reviews.verified_purchase: the reviewer had a DELIVERED order of the product.
 *    Existing reviews stay as they are (visible); only the ones whose author has
 *    a delivered order of that product get the badge.
 */

CREATE TABLE saved_searches (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    filters JSON NOT NULL,
    filters_hash VARCHAR(64) NOT NULL,
    alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_product_id BIGINT NOT NULL DEFAULT 0,
    last_notified_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_saved_search_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_saved_search_filters UNIQUE (user_id, filters_hash),
    INDEX idx_saved_search_alerts (alerts_enabled, id)
);

ALTER TABLE notification_inbox
    ADD COLUMN previous_amount DECIMAL(12,2) NULL AFTER amount;

ALTER TABLE reviews
    ADD COLUMN verified_purchase BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE reviews r
SET r.verified_purchase = TRUE
WHERE EXISTS (
    SELECT 1
    FROM orders o
    JOIN order_items i ON i.order_id = o.id
    WHERE o.user_id = r.user_id
      AND i.product_id = r.product_id
      AND o.status = 'DELIVERED'
);
