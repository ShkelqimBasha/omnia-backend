ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users
SET email_verified = TRUE;

CREATE TABLE email_verification_tokens
(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    user_id BIGINT NOT NULL,

    token VARCHAR(255) NOT NULL UNIQUE,

    expires_at DATETIME NOT NULL,

    used BOOLEAN NOT NULL DEFAULT FALSE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_email_verification_user
        FOREIGN KEY (user_id)
            REFERENCES users(id)
            ON DELETE CASCADE
);

CREATE INDEX idx_email_verification_token
    ON email_verification_tokens(token);

CREATE INDEX idx_email_verification_user
    ON email_verification_tokens(user_id);