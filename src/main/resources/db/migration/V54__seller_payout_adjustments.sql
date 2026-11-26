SET NAMES utf8mb4;

/*
 * Refunds after a payout, 2026-11-26. When an order that was already paid out to its seller is refunded
 * (fully, partly or by a PayPal reversal), the seller owes that money back: one adjustment per refund step
 * and order, amount = refunded part x (1 - commission percent of that order's payout).
 *
 * Adjustments are settled by later payouts (deducted from their net) or manually by the admin (recovered
 * from the seller, or waived). Nothing is deleted or edited: every settlement is its own row.
 *   remaining = amount - SUM(settlements.amount); settled_at is set once remaining reaches 0.
 * Idempotent: (order_id, refund_key) is unique; refund_key names the cumulative refunded amount of the order
 * ("REFUND:12.30"), so the same refund reported again never creates a second adjustment.
 */

CREATE TABLE seller_payout_adjustments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    payout_id BIGINT NOT NULL,
    refund_key VARCHAR(80) NOT NULL,
    source VARCHAR(20) NOT NULL,
    refunded_amount DECIMAL(12,2) NOT NULL,
    commission_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    amount DECIMAL(12,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    settled_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    settled_at DATETIME(6) NULL,
    settled_in_payout_id BIGINT NULL,
    CONSTRAINT fk_payout_adj_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_payout_adj_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_payout_adj_payout FOREIGN KEY (payout_id) REFERENCES seller_payouts (id),
    CONSTRAINT fk_payout_adj_settled_payout FOREIGN KEY (settled_in_payout_id) REFERENCES seller_payouts (id),
    CONSTRAINT fk_payout_adj_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uk_payout_adj_refund UNIQUE (order_id, refund_key),
    CONSTRAINT chk_payout_adj_amounts CHECK (refunded_amount > 0 AND amount >= 0
        AND settled_amount >= 0 AND settled_amount <= amount),
    INDEX idx_payout_adj_org_open (organization_id, settled_at, id),
    INDEX idx_payout_adj_created (created_at, id)
);

CREATE TABLE seller_payout_adjustment_settlements (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    adjustment_id BIGINT NOT NULL,
    kind VARCHAR(12) NOT NULL,
    payout_id BIGINT NULL,
    amount DECIMAL(12,2) NOT NULL,
    note VARCHAR(500) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_payout_adj_settle_adj FOREIGN KEY (adjustment_id) REFERENCES seller_payout_adjustments (id),
    CONSTRAINT fk_payout_adj_settle_payout FOREIGN KEY (payout_id) REFERENCES seller_payouts (id),
    CONSTRAINT fk_payout_adj_settle_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_payout_adj_settle CHECK (amount > 0 AND kind IN ('PAYOUT', 'RECOVERED', 'WAIVED')
        AND ((kind = 'PAYOUT') = (payout_id IS NOT NULL))),
    INDEX idx_payout_adj_settle_adj (adjustment_id, id),
    INDEX idx_payout_adj_settle_payout (payout_id)
);

/*
 * A payout now records what was deducted for open adjustments: transfer = net - deduction (never negative).
 * reference_key: the normalised reference of payouts recorded from now on; one transfer reference per seller.
 * Rows recorded before V54 keep it NULL (they are still checked by the service), so this cannot fail on
 * existing data.
 */
ALTER TABLE seller_payouts
    ADD COLUMN deduction_amount DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER net_amount,
    ADD COLUMN transfer_amount DECIMAL(12,2) NULL AFTER deduction_amount,
    ADD COLUMN reference_key VARCHAR(100) NULL AFTER reference;

UPDATE seller_payouts SET transfer_amount = net_amount WHERE transfer_amount IS NULL;

ALTER TABLE seller_payouts
    MODIFY COLUMN transfer_amount DECIMAL(12,2) NOT NULL,
    ADD CONSTRAINT chk_seller_payout_transfer CHECK (deduction_amount >= 0 AND transfer_amount >= 0
        AND transfer_amount = net_amount - deduction_amount),
    ADD CONSTRAINT uk_seller_payout_reference UNIQUE (organization_id, reference_key);
