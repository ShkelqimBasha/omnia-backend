DELETE FROM refresh_tokens;

ALTER TABLE refresh_tokens
    CHANGE COLUMN token token_hash VARCHAR(64) NOT NULL;

ALTER TABLE refresh_tokens
    RENAME INDEX uk_refresh_token TO uk_refresh_token_hash;