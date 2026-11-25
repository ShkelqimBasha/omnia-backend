SET NAMES utf8mb4;

/*
 * Seller payouts, 2026-11-25. Omnia collects PayPal payments for every seller into its own account;
 * the platform admin records each bank transfer to a seller here. An order is paid out at most once
 * (orders.payout_id). Cash on delivery is collected by the seller and is never owed.
 *   gross = sum of the orders' total_amount, commission = gross * commission_percent / 100 (HALF_UP,
 *   cents), net = gross - commission (what was transferred).
 */

CREATE TABLE seller_payouts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    gross_amount DECIMAL(12,2) NOT NULL,
    commission_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    commission_amount DECIMAL(12,2) NOT NULL,
    net_amount DECIMAL(12,2) NOT NULL,
    order_count INT NOT NULL,
    reference VARCHAR(100) NULL,
    note VARCHAR(500) NULL,
    paid_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_seller_payout_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_seller_payout_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_seller_payout_amounts CHECK (gross_amount >= 0 AND commission_amount >= 0
        AND net_amount >= 0 AND net_amount = gross_amount - commission_amount AND order_count > 0),
    INDEX idx_seller_payout_org (organization_id, paid_at),
    INDEX idx_seller_payout_paid (paid_at)
);

ALTER TABLE orders
    ADD COLUMN payout_id BIGINT NULL,
    ADD CONSTRAINT fk_orders_payout FOREIGN KEY (payout_id) REFERENCES seller_payouts (id),
    ADD INDEX idx_orders_org_payout (organization_id, payout_id, status);
