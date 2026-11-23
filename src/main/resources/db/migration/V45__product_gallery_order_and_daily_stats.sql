SET NAMES utf8mb4;

/*
 * Seller tools (2026-11-23).
 *
 * 1. Product photo gallery: every image gets a position. The cover is the
 *    primary image and always sits at position 0.
 * 2. Per-product daily counters for the seller's statistics. One row per
 *    product and day; the application adds to it in small batches so the
 *    product page never waits for it.
 */

/* 1. Gallery order */
ALTER TABLE product_images
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER is_primary;

/* Existing images: cover first, then the order they were added in. */
UPDATE product_images image
    JOIN (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY product_id
                   ORDER BY is_primary DESC, id ASC
               ) - 1 AS position
        FROM product_images
    ) ordered ON ordered.id = image.id
SET image.sort_order = ordered.position;

CREATE INDEX idx_product_images_product_sort
    ON product_images (product_id, sort_order, id);

/* 2. Daily counters */
CREATE TABLE product_daily_stats (
    product_id BIGINT NOT NULL,
    stat_date  DATE   NOT NULL,
    views      INT    NOT NULL DEFAULT 0,
    cart_adds  INT    NOT NULL DEFAULT 0,
    favorites  INT    NOT NULL DEFAULT 0,
    inquiries  INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, stat_date),
    INDEX idx_product_daily_stats_date (stat_date),
    CONSTRAINT fk_product_daily_stats_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
