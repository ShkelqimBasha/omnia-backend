-- Segmented push campaigns for platform admins, and the per-category interest they segment on.

-- One row per (user, category): which categories a signed-in user looked at, and when last.
-- Only an aggregate (no product ids, no history) so a campaign can target "viewed Makina".
CREATE TABLE user_category_views (
 user_id BIGINT NOT NULL,
 category_id BIGINT NOT NULL,
 view_count INT NOT NULL DEFAULT 1,
 last_viewed_at DATETIME(6) NOT NULL,
 PRIMARY KEY (user_id, category_id),
 CONSTRAINT fk_category_view_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT fk_category_view_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE,
 INDEX idx_category_view_recent (category_id, last_viewed_at)
);

CREATE TABLE push_campaigns (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 title VARCHAR(65) NOT NULL,
 body VARCHAR(180) NOT NULL,
 link_path VARCHAR(255) NULL,
 -- Segment. NULL / 'ALL' means "no filter" for that dimension.
 category_id BIGINT NULL,
 interest VARCHAR(12) NOT NULL DEFAULT 'ANY',
 active_days INT NOT NULL DEFAULT 90,
 city VARCHAR(100) NULL,
 platform VARCHAR(12) NOT NULL DEFAULT 'ALL',
 -- DRAFT -> TESTED (test sent to the admin) -> SCHEDULED -> SENDING -> SENT; CANCELLED from DRAFT/TESTED/SCHEDULED.
 status VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
 scheduled_at DATETIME(6) NULL,
 tested_at DATETIME(6) NULL,
 tested_by BIGINT NULL,
 sent_at DATETIME(6) NULL,
 recipient_count INT NULL,
 created_by BIGINT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_campaign_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
 CONSTRAINT fk_campaign_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
 CONSTRAINT fk_campaign_tester FOREIGN KEY (tested_by) REFERENCES users(id) ON DELETE SET NULL,
 INDEX idx_campaign_due (status, scheduled_at)
);

-- Who received which campaign: the frequency cap reads this (test sends are never recorded here).
CREATE TABLE push_campaign_recipients (
 campaign_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 sent_at DATETIME(6) NOT NULL,
 PRIMARY KEY (campaign_id, user_id),
 CONSTRAINT fk_campaign_recipient_campaign FOREIGN KEY (campaign_id) REFERENCES push_campaigns(id) ON DELETE CASCADE,
 CONSTRAINT fk_campaign_recipient_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 INDEX idx_campaign_recipient_cap (user_id, sent_at)
);

-- Campaign traffic (priority 1) never delays order/stock notices (priority 0) in the push outbox.
ALTER TABLE push_outbox ADD COLUMN priority TINYINT NOT NULL DEFAULT 0;
CREATE INDEX idx_push_outbox_priority ON push_outbox (state, priority, notification_id);
