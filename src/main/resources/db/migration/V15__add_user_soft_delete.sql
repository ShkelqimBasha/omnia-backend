ALTER TABLE users
    ADD COLUMN deleted_at DATETIME NULL AFTER updated_at,
    ADD INDEX idx_users_deleted_at (deleted_at);