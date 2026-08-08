ALTER TABLE orders
    ADD COLUMN shipping_name VARCHAR(150) NULL AFTER address_id,
    ADD COLUMN shipping_email VARCHAR(150) NULL AFTER shipping_name,
    ADD COLUMN shipping_phone VARCHAR(30) NULL AFTER shipping_email,
    ADD COLUMN shipping_address VARCHAR(500) NULL AFTER shipping_phone;