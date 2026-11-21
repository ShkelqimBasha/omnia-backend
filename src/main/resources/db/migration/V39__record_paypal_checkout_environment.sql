-- Each order checkout records the PayPal environment it was created in, so a checkout is only ever
-- sent to that environment (a Sandbox order ID is never looked up, captured or reconciled at PayPal
-- Live, and the other way round).
-- Every existing checkout was created by the Sandbox-only backend, so the default marks all of them
-- as Sandbox; new checkouts store the server's environment explicitly. State, PayPal order ID,
-- attempt marker and recovery history are untouched. ADD COLUMN with a constant default is an
-- instant change in MySQL 8.4 and does not rewrite updated_at.
ALTER TABLE paypal_checkouts
    ADD COLUMN environment VARCHAR(8) NOT NULL DEFAULT 'sandbox' AFTER amount;

ALTER TABLE paypal_checkouts
    ADD CONSTRAINT ck_paypal_checkout_environment CHECK (environment IN ('sandbox','live'));
