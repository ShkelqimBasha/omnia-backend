-- A PayPal create request can succeed remotely while the local transaction that would store the
-- PayPal order ID times out or rolls back. A missing paypal_order_id therefore does not prove that
-- PayPal never created an order. The backend now commits this marker before it may send a create
-- request, so a NULL marker is positive evidence that no create request was ever sent.
ALTER TABLE paypal_checkouts ADD COLUMN paypal_create_attempted_at DATETIME(6) NULL AFTER approval_url;

-- Existing rows predate the marker. Every checkout was synced, and so could have reached PayPal,
-- right after it was inserted, so nothing proves a create request was never sent. Mark all of them
-- as possibly attempted. created_at is the earliest moment a request could have been sent.
-- State, request key, PayPal order ID and recovery events are left untouched. updated_at is
-- assigned explicitly so its ON UPDATE CURRENT_TIMESTAMP does not rewrite every row's timestamp.
UPDATE paypal_checkouts SET paypal_create_attempted_at = created_at, updated_at = updated_at;
