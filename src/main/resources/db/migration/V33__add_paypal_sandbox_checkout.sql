CREATE TABLE paypal_checkouts (
 id CHAR(36) PRIMARY KEY,
 user_id BIGINT NOT NULL,
 request_key CHAR(36) NOT NULL,
 request_hash CHAR(64) NOT NULL,
 checkout_group_id BIGINT NOT NULL,
 amount DECIMAL(10,2) NOT NULL,
 paypal_order_id VARCHAR(64) NULL,
 approval_url VARCHAR(2048) NULL,
 state VARCHAR(24) NOT NULL DEFAULT 'CREATING',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_paypal_user_request(user_id,request_key),
 UNIQUE KEY uk_paypal_checkout(checkout_group_id),
 UNIQUE KEY uk_paypal_provider_order(paypal_order_id),
 KEY ix_paypal_reconcile(state,updated_at),
 CONSTRAINT fk_paypal_user FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT fk_paypal_checkout FOREIGN KEY(checkout_group_id) REFERENCES checkout_groups(id)
);

-- Cancelled request keys cannot create orders after a network timeout.
CREATE TABLE paypal_cancelled_requests (
 user_id BIGINT NOT NULL,
 request_key CHAR(36) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(user_id,request_key),
 CONSTRAINT fk_paypal_cancelled_user FOREIGN KEY(user_id) REFERENCES users(id)
);
