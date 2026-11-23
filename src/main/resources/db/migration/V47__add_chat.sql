SET NAMES utf8mb4;

/*
 * Chat between a buyer and the company selling a product ("Shkruaj shitësit").
 *
 * One conversation per (buyer, company, product). Each side has its own read
 * marker: the id of the last message that side has read. The seller side is
 * shared by the company's owners and admins.
 */
CREATE TABLE chat_conversations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    buyer_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    last_message_id BIGINT NULL,
    last_message_at DATETIME(6) NULL,
    last_message_preview VARCHAR(160) NULL,
    last_sender_side VARCHAR(10) NULL,
    buyer_last_read_id BIGINT NOT NULL DEFAULT 0,
    seller_last_read_id BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_conversation UNIQUE (buyer_id, organization_id, product_id),
    KEY idx_chat_conversations_org (organization_id, last_message_id),
    KEY idx_chat_conversations_buyer (buyer_id, last_message_id),
    CONSTRAINT fk_chat_conversations_buyer FOREIGN KEY (buyer_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_conversations_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_conversations_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

/* Text only, at most 2000 characters. sender_side is BUYER or SELLER. */
CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NULL,
    sender_side VARCHAR(10) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chat_messages_conversation (conversation_id, id),
    KEY idx_chat_messages_sender (sender_id, created_at),
    CONSTRAINT chk_chat_messages_side CHECK (sender_side IN ('BUYER', 'SELLER')),
    CONSTRAINT fk_chat_messages_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

/* Opening a chat notice is answered by the chat itself; this index keeps the "one unread notice per chat" check cheap. */
CREATE INDEX idx_notification_target ON notification_inbox (recipient_id, target_type, target_id, read_at);
