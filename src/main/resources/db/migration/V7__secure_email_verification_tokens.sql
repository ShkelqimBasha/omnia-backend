DELETE FROM email_verification_tokens;

ALTER TABLE email_verification_tokens
    CHANGE COLUMN token token_hash VARCHAR(64) NOT NULL;

DROP INDEX idx_email_verification_token
    ON email_verification_tokens;