CREATE TABLE listing_promotion_payments (
    reference_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    product_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    duration_days INT NOT NULL,
    provider_order_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin UNIQUE,
    merchant_id VARCHAR(64),
    approval_url VARCHAR(2048),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
