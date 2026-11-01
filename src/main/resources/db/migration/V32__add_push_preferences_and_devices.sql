CREATE TABLE push_preferences (
 user_id BIGINT PRIMARY KEY,
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 orders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 payments_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 offers_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 favorites_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 admin_orders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 stock_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 audit_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 reports_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 quiet_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 quiet_start INT NOT NULL DEFAULT 1320,
 quiet_end INT NOT NULL DEFAULT 480,
 time_zone VARCHAR(80) NOT NULL DEFAULT 'UTC',
 sound_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 vibration_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE push_devices (
 installation_id CHAR(36) PRIMARY KEY,
 secret_hash CHAR(64) NOT NULL,
 user_id BIGINT NULL,
 token TEXT NULL,
 token_hash CHAR(64) NULL UNIQUE,
 binding_version BIGINT NOT NULL,
 binding_tag CHAR(36) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 last_seen DATETIME(6) NOT NULL,
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
 INDEX idx_push_device_user(user_id,enabled)
);
CREATE TABLE push_outbox (
 notification_id BIGINT PRIMARY KEY,
 state VARCHAR(12) NOT NULL DEFAULT 'PENDING',
 available_at DATETIME(6) NOT NULL,
 lease_until DATETIME(6) NULL,
 attempts INT NOT NULL DEFAULT 0,
 FOREIGN KEY (notification_id) REFERENCES notification_inbox(id) ON DELETE CASCADE,
 INDEX idx_push_outbox_due(state,available_at,lease_until)
);
CREATE TABLE push_deliveries (
 notification_id BIGINT NOT NULL,
 installation_id CHAR(36) NOT NULL,
 binding_version BIGINT NOT NULL,
 PRIMARY KEY(notification_id,installation_id,binding_version),
 FOREIGN KEY (notification_id) REFERENCES notification_inbox(id) ON DELETE CASCADE,
 FOREIGN KEY (installation_id) REFERENCES push_devices(installation_id) ON DELETE CASCADE
);
