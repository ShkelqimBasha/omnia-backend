CREATE TABLE order_status_history (
                                      id BIGINT NOT NULL AUTO_INCREMENT,
                                      order_id BIGINT NOT NULL,
                                      from_status ENUM(
        'PENDING',
        'CONFIRMED',
        'PROCESSING',
        'SHIPPED',
        'DELIVERED',
        'CANCELLED'
    ) NULL,
                                      to_status ENUM(
        'PENDING',
        'CONFIRMED',
        'PROCESSING',
        'SHIPPED',
        'DELIVERED',
        'CANCELLED'
    ) NOT NULL,
                                      changed_by_user_id BIGINT NULL,
                                      changed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                      PRIMARY KEY (id),
                                      KEY idx_order_status_history_order (
        order_id,
        changed_at
    ),
                                      KEY idx_order_status_history_user (
        changed_by_user_id
    ),
                                      CONSTRAINT fk_order_status_history_order
                                          FOREIGN KEY (order_id)
                                              REFERENCES orders (id)
                                              ON DELETE CASCADE,
                                      CONSTRAINT fk_order_status_history_user
                                          FOREIGN KEY (changed_by_user_id)
                                              REFERENCES users (id)
                                              ON DELETE SET NULL
);

INSERT INTO order_status_history (
    order_id,
    from_status,
    to_status,
    changed_by_user_id,
    changed_at
)
SELECT
    id,
    NULL,
    status,
    NULL,
    COALESCE(
            created_at,
            CURRENT_TIMESTAMP(6)
    )
FROM orders;