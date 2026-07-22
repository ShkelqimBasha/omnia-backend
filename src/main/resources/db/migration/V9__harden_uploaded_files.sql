ALTER TABLE uploaded_files
DROP COLUMN path;

ALTER TABLE uploaded_files
    MODIFY COLUMN stored_name VARCHAR(100) NOT NULL,
    MODIFY COLUMN content_type VARCHAR(100) NOT NULL;

ALTER TABLE uploaded_files
    ADD COLUMN checksum_sha256 CHAR(64) NULL
        AFTER size;

CREATE INDEX idx_uploaded_files_uploaded_at
    ON uploaded_files (uploaded_at);