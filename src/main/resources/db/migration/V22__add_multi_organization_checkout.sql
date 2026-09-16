CREATE TABLE checkout_groups
(
    id BIGINT NOT NULL AUTO_INCREMENT,

    checkout_reference VARCHAR(36) NOT NULL,

    user_id BIGINT NOT NULL,

    order_count INT NOT NULL DEFAULT 0,

    subtotal_amount DECIMAL(10, 2)
        NOT NULL DEFAULT 0.00,

    shipping_fee DECIMAL(10, 2)
        NOT NULL DEFAULT 0.00,

    discount_amount DECIMAL(10, 2)
        NOT NULL DEFAULT 0.00,

    total_amount DECIMAL(10, 2)
        NOT NULL DEFAULT 0.00,

    coupon_code VARCHAR(50) NULL,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT uk_checkout_groups_reference
        UNIQUE (checkout_reference),

    KEY idx_checkout_groups_user_id (user_id),

    CONSTRAINT fk_checkout_groups_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE checkout_group_coupons
(
    id BIGINT NOT NULL AUTO_INCREMENT,

    checkout_group_id BIGINT NOT NULL,

    coupon_id BIGINT NOT NULL,

    discount_amount DECIMAL(10, 2)
        NOT NULL DEFAULT 0.00,

    PRIMARY KEY (id),

    CONSTRAINT uk_checkout_group_coupons_group
        UNIQUE (checkout_group_id),

    KEY idx_checkout_group_coupons_coupon_id
        (coupon_id),

    CONSTRAINT fk_checkout_group_coupons_group
        FOREIGN KEY (checkout_group_id)
            REFERENCES checkout_groups (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_checkout_group_coupons_coupon
        FOREIGN KEY (coupon_id)
            REFERENCES coupons (id)
            ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


ALTER TABLE orders
    ADD COLUMN checkout_group_id BIGINT NULL
        AFTER user_id,

    ADD COLUMN checkout_sequence INT NULL
        AFTER checkout_group_id,

    ADD COLUMN organization_id BIGINT NULL
        AFTER checkout_sequence,

    ADD COLUMN organization_name VARCHAR(150) NULL
        AFTER organization_id,

    ADD KEY idx_orders_checkout_group_id
        (checkout_group_id),

    ADD KEY idx_orders_organization_id
        (organization_id),

    ADD CONSTRAINT fk_orders_checkout_group
        FOREIGN KEY (checkout_group_id)
            REFERENCES checkout_groups (id)
            ON DELETE SET NULL,

    ADD CONSTRAINT fk_orders_organization
        FOREIGN KEY (organization_id)
            REFERENCES organizations (id)
            ON DELETE SET NULL;