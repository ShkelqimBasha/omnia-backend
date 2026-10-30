ALTER TABLE organizations
    ADD COLUMN editor_name_key VARCHAR(150) NULL,
    ADD UNIQUE KEY uk_organizations_editor_name (editor_name_key),
    ADD COLUMN business_email VARCHAR(150) NULL,
    ADD COLUMN phone VARCHAR(30) NULL,
    ADD COLUMN address VARCHAR(500) NULL,
    ADD COLUMN primary_administrator_id BIGINT NULL,
    ADD COLUMN editor_revision BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_organizations_primary_admin FOREIGN KEY (primary_administrator_id) REFERENCES users(id) ON DELETE SET NULL;

UPDATE organizations o
JOIN (
    SELECT m.organization_id, MIN(m.user_id) AS owner_id
    FROM organization_members m
    JOIN users u ON u.id = m.user_id AND u.deleted_at IS NULL
    WHERE m.membership_role = 'OWNER' AND m.status = 'ACTIVE'
    GROUP BY m.organization_id
) owners ON owners.organization_id = o.id
SET o.primary_administrator_id = owners.owner_id;

-- Legacy duplicate names remain untouched. Editor writes require a conflict-free name.
UPDATE organizations o
JOIN (
    SELECT LOWER(TRIM(name)) AS name_key
    FROM organizations
    GROUP BY LOWER(TRIM(name))
    HAVING COUNT(*) = 1
) unique_names ON LOWER(TRIM(o.name)) = unique_names.name_key
SET o.editor_name_key = unique_names.name_key;
