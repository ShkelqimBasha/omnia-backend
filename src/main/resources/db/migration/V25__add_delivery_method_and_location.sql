ALTER TABLE orders
    ADD COLUMN delivery_method VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN shipping_latitude DECIMAL(10,7) NULL,
    ADD COLUMN shipping_longitude DECIMAL(10,7) NULL,
    ADD COLUMN express_surcharge DECIMAL(10,2) NOT NULL DEFAULT 0.00;
