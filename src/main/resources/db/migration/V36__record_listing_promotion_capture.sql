ALTER TABLE listing_promotion_payments
    ADD COLUMN capture_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin UNIQUE,
    ADD COLUMN paid_at TIMESTAMP NULL,
    ADD COLUMN promoted_until TIMESTAMP NULL;
