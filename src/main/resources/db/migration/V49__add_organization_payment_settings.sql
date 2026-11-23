SET NAMES utf8mb4;

/*
 * Payment methods per company (2026-11-23).
 *
 * Each company chooses whether it accepts cash on delivery and/or PayPal; at least one
 * stays on. Defaults keep today's behaviour (both accepted, no COD limits).
 * The optional COD minimum/maximum apply to the value of that company's products in a
 * checkout (before coupon and delivery).
 */
ALTER TABLE organizations
    ADD COLUMN accepts_cash_on_delivery BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN accepts_paypal BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN cod_min_order_amount DECIMAL(10, 2) NULL,
    ADD COLUMN cod_max_order_amount DECIMAL(10, 2) NULL,
    ADD CONSTRAINT chk_organizations_payment_method_any
        CHECK (accepts_cash_on_delivery OR accepts_paypal),
    ADD CONSTRAINT chk_organizations_cod_limits_positive
        CHECK ((cod_min_order_amount IS NULL OR cod_min_order_amount >= 0)
            AND (cod_max_order_amount IS NULL OR cod_max_order_amount > 0)),
    ADD CONSTRAINT chk_organizations_cod_limits_order
        CHECK (cod_min_order_amount IS NULL OR cod_max_order_amount IS NULL
            OR cod_min_order_amount <= cod_max_order_amount);
