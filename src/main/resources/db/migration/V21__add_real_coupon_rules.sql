ALTER TABLE coupons
    MODIFY COLUMN discount_type ENUM(
    'PERCENTAGE',
    'FIXED',
    'FREE_SHIPPING'
    ) NOT NULL,
    ADD COLUMN minimum_order_amount DECIMAL(10, 2)
    NOT NULL DEFAULT 0.00
    AFTER discount_value,
    ADD COLUMN per_user_limit INT NULL
    AFTER usage_limit;

DELETE first_usage
FROM order_coupons AS first_usage
INNER JOIN order_coupons AS duplicate_usage
        ON duplicate_usage.order_id = first_usage.order_id
       AND duplicate_usage.id < first_usage.id;

ALTER TABLE order_coupons
    ADD CONSTRAINT uq_order_coupons_order
        UNIQUE (order_id);

INSERT INTO coupons (
    code,
    discount_type,
    discount_value,
    minimum_order_amount,
    start_date,
    end_date,
    usage_limit,
    per_user_limit,
    status
)
VALUES
    (
        'OMNIA10',
        'PERCENTAGE',
        10.00,
        0.00,
        NULL,
        NULL,
        NULL,
        NULL,
        'ACTIVE'
    ),
    (
        'WELCOME5',
        'FIXED',
        5.00,
        0.00,
        NULL,
        NULL,
        NULL,
        1,
        'ACTIVE'
    ),
    (
        'FREE',
        'FREE_SHIPPING',
        0.00,
        0.00,
        NULL,
        NULL,
        NULL,
        NULL,
        'ACTIVE'
    )
    ON DUPLICATE KEY UPDATE
                         discount_type = VALUES(discount_type),
                         discount_value = VALUES(discount_value),
                         minimum_order_amount = VALUES(minimum_order_amount),
                         per_user_limit = VALUES(per_user_limit),
                         status = VALUES(status);

INSERT INTO order_coupons (
    order_id,
    coupon_id,
    discount_amount
)
SELECT
    order_record.id,
    coupon.id,
    order_record.discount_amount
FROM orders AS order_record
         INNER JOIN coupons AS coupon
                    ON coupon.code = order_record.coupon_code
WHERE order_record.coupon_code IS NOT NULL
  AND TRIM(order_record.coupon_code) <> ''
    ON DUPLICATE KEY UPDATE
                         coupon_id = VALUES(coupon_id),
                         discount_amount = VALUES(discount_amount);