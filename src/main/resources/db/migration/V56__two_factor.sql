SET NAMES utf8mb4;

/*
 * Two-step sign-in (TOTP, RFC 6238) — opt-in per account.
 *
 * user_two_factor: one row per user who started setup. enabled_at is NULL while the
 * user has scanned the code but not yet confirmed it. last_used_step stops a code from
 * being used twice.
 *
 * user_two_factor_recovery: one-time recovery codes, stored as SHA-256 hashes.
 */
CREATE TABLE user_two_factor (
    user_id BIGINT NOT NULL PRIMARY KEY,
    secret VARCHAR(64) NOT NULL,
    enabled_at DATETIME(6) NULL,
    last_used_step BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_user_two_factor_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_two_factor_recovery (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code_hash CHAR(64) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_user_two_factor_recovery_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_two_factor_recovery (user_id, code_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
