ALTER TABLE users
    ADD COLUMN google_uid34 VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD UNIQUE KEY uk_users_google_uid34 (google_uid34);
