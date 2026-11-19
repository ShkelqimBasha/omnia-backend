-- Audited recovery for PayPal checkouts in RECOVERY_REQUIRED, review cases for late or
-- conflicting captures, scoped admin idempotency keys and webhook delivery de-duplication.
-- Evidence columns hold sanitized PayPal facts only: no payer, address, token or link data.

CREATE TABLE paypal_recovery_events (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 checkout_id CHAR(36) NOT NULL,
 event_type VARCHAR(40) NOT NULL,
 actor_user_id BIGINT NULL,
 from_state VARCHAR(24) NULL,
 to_state VARCHAR(24) NULL,
 reason VARCHAR(1000) NULL,
 evidence JSON NULL,
 evidence_fingerprint CHAR(64) NULL,
 details VARCHAR(2000) NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_paypal_recovery_checkout (checkout_id, id),
 CONSTRAINT fk_paypal_recovery_checkout FOREIGN KEY (checkout_id) REFERENCES paypal_checkouts(id),
 CONSTRAINT fk_paypal_recovery_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
);

-- Idempotency keys are scoped to one checkout and one operation.
CREATE TABLE paypal_recovery_requests (
 checkout_id CHAR(36) NOT NULL,
 operation VARCHAR(40) NOT NULL,
 idempotency_key CHAR(36) NOT NULL,
 request_hash CHAR(64) NOT NULL,
 actor_user_id BIGINT NOT NULL,
 response JSON NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY (checkout_id, operation, idempotency_key),
 CONSTRAINT fk_paypal_recovery_request_checkout FOREIGN KEY (checkout_id) REFERENCES paypal_checkouts(id),
 CONSTRAINT fk_paypal_recovery_request_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
);

-- One case per checkout, kind and capture; a repeated report of the same fact cannot open another,
-- and a closed case stays closed. Closing records who, when, the documented outcome and why; the
-- evidence column is never rewritten.
CREATE TABLE paypal_review_cases (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 checkout_id CHAR(36) NOT NULL,
 kind VARCHAR(32) NOT NULL,
 capture_key VARCHAR(80) NOT NULL DEFAULT '',
 status VARCHAR(12) NOT NULL DEFAULT 'OPEN',
 evidence JSON NULL,
 details VARCHAR(2000) NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 closed_by_user_id BIGINT NULL,
 closed_at DATETIME(6) NULL,
 closure_outcome VARCHAR(32) NULL,
 closure_reason VARCHAR(1000) NULL,
 UNIQUE KEY uk_paypal_review_case (checkout_id, kind, capture_key),
 KEY ix_paypal_review_status (status, id),
 CONSTRAINT fk_paypal_review_checkout FOREIGN KEY (checkout_id) REFERENCES paypal_checkouts(id),
 CONSTRAINT fk_paypal_review_closed_by FOREIGN KEY (closed_by_user_id) REFERENCES users(id),
 CONSTRAINT ck_paypal_review_status CHECK (status IN ('OPEN','CLOSED')),
 CONSTRAINT ck_paypal_review_closure CHECK ((status = 'OPEN' AND closed_at IS NULL AND closure_outcome IS NULL)
     OR (status = 'CLOSED' AND closed_at IS NOT NULL AND closure_outcome IS NOT NULL AND closure_reason IS NOT NULL))
);

-- A redelivered webhook (same PayPal event ID) is a no-op for each checkout it touched.
CREATE TABLE paypal_webhook_events (
 event_id VARCHAR(64) NOT NULL,
 checkout_id CHAR(36) NOT NULL,
 event_type VARCHAR(64) NOT NULL,
 outcome VARCHAR(32) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY (event_id, checkout_id),
 CONSTRAINT fk_paypal_webhook_checkout FOREIGN KEY (checkout_id) REFERENCES paypal_checkouts(id)
);

-- Rows moved to RECOVERY_REQUIRED by hand before this audit existed.
INSERT INTO paypal_recovery_events (checkout_id, event_type, from_state, to_state, reason)
SELECT id, 'LEGACY_MANUAL', NULL, 'RECOVERY_REQUIRED',
       'State set outside the application before audited recovery existed'
FROM paypal_checkouts
WHERE state = 'RECOVERY_REQUIRED';
