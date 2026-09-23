ALTER TABLE coupons
    ADD COLUMN organization_id BIGINT NULL
        AFTER id,

    ADD KEY idx_coupons_organization_id
        (organization_id),

    ADD CONSTRAINT fk_coupons_organization
        FOREIGN KEY (organization_id)
            REFERENCES organizations (id)
            ON DELETE RESTRICT;