SET NAMES utf8mb4;

/*
 * Omnia (the platform admins) in the chat.
 *
 * - Admins may write in any conversation; their messages are stored with sender_side 'ADMIN'.
 * - A SUPPORT conversation is Omnia <-> one user, with no company and no product.
 *   It has no organization_id or product_id; product_name holds 'Omnia'. The admin side
 *   uses seller_last_read_id as its read marker. Existing rows stay PRODUCT.
 *
 * One SUPPORT conversation per user is enforced by the service (it locks the user row
 * before find-or-create): the unique key on (buyer_id, organization_id, product_id) does not
 * cover NULLs, and MySQL refuses a stored generated column over buyer_id's cascading foreign key.
 */
ALTER TABLE chat_messages DROP CHECK chk_chat_messages_side;
ALTER TABLE chat_messages
    ADD CONSTRAINT chk_chat_messages_side CHECK (sender_side IN ('BUYER', 'SELLER', 'ADMIN'));

ALTER TABLE chat_conversations
    ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'PRODUCT' AFTER id,
    MODIFY organization_id BIGINT NULL,
    MODIFY product_id BIGINT NULL,
    ADD CONSTRAINT chk_chat_conversations_kind CHECK (kind IN ('PRODUCT', 'SUPPORT'));

/* The admin list: every conversation with messages, newest activity first. */
CREATE INDEX idx_chat_conversations_activity ON chat_conversations (last_message_id);
CREATE INDEX idx_chat_conversations_support ON chat_conversations (buyer_id, kind);
