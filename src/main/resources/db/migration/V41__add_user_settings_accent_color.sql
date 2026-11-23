-- Buyer-selectable accent palette (web first, Android later). Existing rows keep the Omnia default.
ALTER TABLE user_settings
    ADD COLUMN accent_color VARCHAR(16) NOT NULL DEFAULT 'omnia';

ALTER TABLE user_settings
    ADD CONSTRAINT chk_user_settings_accent_color
        CHECK (accent_color IN ('omnia', 'orange', 'emerald', 'pink', 'graphite'));
