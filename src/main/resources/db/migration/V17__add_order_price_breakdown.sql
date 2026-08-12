ALTER TABLE orders
    ADD COLUMN subtotal_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 AFTER total_amount,
    ADD COLUMN shipping_fee DECIMAL(10, 2) NOT NULL DEFAULT 0.00 AFTER subtotal_amount,
    ADD COLUMN discount_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 AFTER shipping_fee,
    ADD COLUMN coupon_code VARCHAR(50) NULL AFTER discount_amount;

UPDATE orders
SET subtotal_amount = total_amount
WHERE subtotal_amount = 0.00;