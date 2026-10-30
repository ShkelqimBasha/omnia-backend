CREATE TABLE user_settings (
    user_id BIGINT NOT NULL PRIMARY KEY,
    full_name VARCHAR(200) NULL,
    phone VARCHAR(30) NULL,
    address VARCHAR(500) NULL,
    theme_mode VARCHAR(10) NOT NULL,
    language_code VARCHAR(2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_user_settings_theme CHECK (theme_mode IN ('system','light','dark')),
    CONSTRAINT chk_user_settings_language CHECK (language_code IN ('sq','en','it')),
    CONSTRAINT chk_user_settings_currency CHECK (currency_code IN ('EUR','USD','ALL'))
);
