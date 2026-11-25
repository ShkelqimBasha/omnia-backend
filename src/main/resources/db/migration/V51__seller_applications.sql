SET NAMES utf8mb4;

/*
 * "Bëhu shitës", 2026-11-25: a logged-in user applies to sell on Omnia; a platform admin approves
 * (which creates the store) or rejects with a reason.
 *   BUSINESS = a company with NIPT; PRIVATE = an individual ("Shitës privat").
 */

ALTER TABLE organizations
    ADD COLUMN seller_type VARCHAR(20) NOT NULL DEFAULT 'BUSINESS';

ALTER TABLE organizations
    ADD CONSTRAINT chk_organizations_seller_type CHECK (seller_type IN ('BUSINESS', 'PRIVATE'));

CREATE TABLE seller_applications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    seller_type VARCHAR(20) NOT NULL,
    store_name VARCHAR(80) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    city VARCHAR(60) NOT NULL,
    nipt VARCHAR(20) NULL,
    /* Comma-separated ids of active top-level categories, e.g. "3,7". */
    category_ids VARCHAR(200) NOT NULL,
    description TEXT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    reject_reason VARCHAR(500) NULL,
    organization_id BIGINT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_seller_application_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_seller_application_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE SET NULL,
    CONSTRAINT fk_seller_application_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_seller_application_type CHECK (seller_type IN ('BUSINESS', 'PRIVATE')),
    CONSTRAINT chk_seller_application_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    INDEX idx_seller_application_status (status, created_at),
    INDEX idx_seller_application_user (user_id)
);
