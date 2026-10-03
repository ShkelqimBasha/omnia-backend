ALTER TABLE organizations ADD COLUMN logo_uploaded_file_id BIGINT NULL;
ALTER TABLE organizations ADD CONSTRAINT fk_organization_logo_file
    FOREIGN KEY (logo_uploaded_file_id) REFERENCES uploaded_files(id) ON DELETE SET NULL;
