DELETE FROM password_reset_tokens;

ALTER TABLE password_reset_tokens
    CHANGE COLUMN token token_hash VARCHAR(64) NOT NULL;

DROP INDEX uk_password_reset_token
    ON password_reset_tokens;

ALTER TABLE password_reset_tokens
    ADD CONSTRAINT uk_password_reset_token_hash
        UNIQUE (token_hash);

ALTER TABLE password_reset_tokens
    ADD CONSTRAINT uk_password_reset_user
        UNIQUE (user_id);