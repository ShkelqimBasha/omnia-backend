/*
 * Add the central SUPER_ADMIN role.
 * Existing ADMIN users are not changed yet.
 */
INSERT INTO roles (name, description)
SELECT 'SUPER_ADMIN', 'Omnia platform administrator'
    WHERE NOT EXISTS (
    SELECT 1
    FROM roles
    WHERE name = 'SUPER_ADMIN'
);


/*
 * Companies or agencies registered in Omnia.
 * Examples: Future 1, Future 2, Remax 1.
 */
CREATE TABLE organizations
(
    id BIGINT NOT NULL AUTO_INCREMENT,

    name VARCHAR(150) NOT NULL,

    slug VARCHAR(160) NOT NULL,

    description TEXT NULL,

    status ENUM(
        'ACTIVE',
        'INACTIVE',
        'SUSPENDED'
    ) NOT NULL DEFAULT 'ACTIVE',

    created_by BIGINT NULL,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT uk_organizations_slug
        UNIQUE (slug),

    CONSTRAINT fk_organizations_created_by
        FOREIGN KEY (created_by)
            REFERENCES users(id)
            ON DELETE SET NULL
);


/*
 * Connects users to one or more organizations.
 *
 * The same user can be:
 * ADMIN in Future 1
 * ADMIN in Future 2
 * STAFF in Remax 1
 */
CREATE TABLE organization_members
(
    id BIGINT NOT NULL AUTO_INCREMENT,

    organization_id BIGINT NOT NULL,

    user_id BIGINT NOT NULL,

    membership_role ENUM(
        'OWNER',
        'ADMIN',
        'STAFF'
    ) NOT NULL DEFAULT 'STAFF',

    status ENUM(
        'ACTIVE',
        'INACTIVE'
    ) NOT NULL DEFAULT 'ACTIVE',

    created_by BIGINT NULL,

    created_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT uk_organization_members_org_user
        UNIQUE (organization_id, user_id),

    CONSTRAINT fk_organization_members_organization
        FOREIGN KEY (organization_id)
            REFERENCES organizations(id)
            ON DELETE CASCADE,

    CONSTRAINT fk_organization_members_user
        FOREIGN KEY (user_id)
            REFERENCES users(id)
            ON DELETE CASCADE,

    CONSTRAINT fk_organization_members_created_by
        FOREIGN KEY (created_by)
            REFERENCES users(id)
            ON DELETE SET NULL
);

CREATE INDEX idx_organization_members_user_status
    ON organization_members(user_id, status);


/*
 * Defines the categories in which an organization
 * is allowed to publish.
 */
CREATE TABLE organization_category_permissions
(
    id BIGINT NOT NULL AUTO_INCREMENT,

    organization_id BIGINT NOT NULL,

    category_id BIGINT NOT NULL,

    can_create BOOLEAN NOT NULL DEFAULT TRUE,

    can_update BOOLEAN NOT NULL DEFAULT TRUE,

    can_delete BOOLEAN NOT NULL DEFAULT FALSE,

    status ENUM(
        'ACTIVE',
        'INACTIVE'
    ) NOT NULL DEFAULT 'ACTIVE',

    granted_by BIGINT NULL,

    created_at TIMESTAMP NOT NULL
                                DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL
                                DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT uk_org_category_permissions
        UNIQUE (organization_id, category_id),

    CONSTRAINT fk_org_category_permissions_organization
        FOREIGN KEY (organization_id)
            REFERENCES organizations(id)
            ON DELETE CASCADE,

    CONSTRAINT fk_org_category_permissions_category
        FOREIGN KEY (category_id)
            REFERENCES categories(id)
            ON DELETE CASCADE,

    CONSTRAINT fk_org_category_permissions_granted_by
        FOREIGN KEY (granted_by)
            REFERENCES users(id)
            ON DELETE SET NULL
);


/*
 * Every new product will belong to an organization
 * and will remember the user who created it.
 *
 * Columns remain nullable temporarily so existing
 * production products are not damaged.
 */
ALTER TABLE products
    ADD COLUMN organization_id BIGINT NULL
        AFTER category_id,

    ADD COLUMN created_by BIGINT NULL
        AFTER organization_id;

CREATE INDEX idx_products_organization
    ON products(organization_id);

CREATE INDEX idx_products_created_by
    ON products(created_by);

CREATE INDEX idx_products_organization_category
    ON products(organization_id, category_id);

ALTER TABLE products
    ADD CONSTRAINT fk_products_organization
        FOREIGN KEY (organization_id)
            REFERENCES organizations(id)
            ON DELETE RESTRICT,

    ADD CONSTRAINT fk_products_created_by
        FOREIGN KEY (created_by)
            REFERENCES users(id)
            ON DELETE SET NULL;