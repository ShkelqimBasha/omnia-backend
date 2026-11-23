SET NAMES utf8mb4;

/*
 * Besueshmëria (trust & safety), 2026-11-23:
 *   1. "Shitës i verifikuar": a company sends its NIPT and a document; the platform admin approves or refuses.
 *   2. "Raporto njoftimin": logged-in users report a product; the admin hides it, dismisses or warns the company.
 *   3. Admin action log: what platform admins changed (never request bodies, secrets or passwords).
 */

/* 1. Verified sellers. organizations.verified_at is set on approval and drives the badge. */
ALTER TABLE organizations
    ADD COLUMN verified_at DATETIME(6) NULL;

CREATE TABLE organization_verifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    nipt VARCHAR(10) NOT NULL,
    /* Private file: served only to platform admins through /api/admin/verifications/{id}/document. */
    document_file_id BIGINT NULL,
    status VARCHAR(12) NOT NULL,
    rejection_reason VARCHAR(500) NULL,
    submitted_by BIGINT NULL,
    submitted_at DATETIME(6) NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    CONSTRAINT fk_org_verification_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_org_verification_file FOREIGN KEY (document_file_id) REFERENCES uploaded_files (id) ON DELETE SET NULL,
    CONSTRAINT fk_org_verification_submitter FOREIGN KEY (submitted_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_org_verification_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_org_verification_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    INDEX idx_org_verification_org (organization_id, id),
    INDEX idx_org_verification_status (status, id),
    INDEX idx_org_verification_file (document_file_id)
);

/* 2. Product reports and the moderation queue. */
CREATE TABLE product_reports (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    organization_id BIGINT NULL,
    reporter_id BIGINT NULL,
    reason VARCHAR(20) NOT NULL,
    details VARCHAR(1000) NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'OPEN',
    resolution_note VARCHAR(500) NULL,
    resolved_by BIGINT NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_product_report_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_report_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE SET NULL,
    CONSTRAINT fk_product_report_reporter FOREIGN KEY (reporter_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_product_report_resolver FOREIGN KEY (resolved_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_product_report_reason CHECK (reason IN ('FRAUD', 'PROHIBITED', 'FAKE_PRICE', 'OTHER')),
    CONSTRAINT chk_product_report_status CHECK (status IN ('OPEN', 'HIDDEN', 'DISMISSED', 'WARNED')),
    INDEX idx_product_report_status (status, id),
    INDEX idx_product_report_product (product_id, status),
    INDEX idx_product_report_reporter (reporter_id, created_at)
);

/* 3. Admin action log. actor_label keeps the name when the user is later deleted. */
CREATE TABLE admin_action_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NULL,
    actor_label VARCHAR(150) NOT NULL,
    http_method VARCHAR(8) NOT NULL,
    route VARCHAR(200) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(64) NULL,
    detail VARCHAR(500) NULL,
    response_status INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_admin_action_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_admin_action_created (created_at, id),
    INDEX idx_admin_action_actor (actor_id, created_at),
    INDEX idx_admin_action_target (target_type, created_at)
);
