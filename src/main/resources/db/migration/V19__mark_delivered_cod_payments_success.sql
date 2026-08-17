UPDATE payments AS payment
    INNER JOIN orders AS order_record
ON order_record.id = payment.order_id
    SET payment.status = 'SUCCESS',
        payment.paid_at = COALESCE(
        payment.paid_at,
        CURRENT_TIMESTAMP
        )
WHERE payment.method = 'CASH_ON_DELIVERY'
  AND payment.status = 'PENDING'
  AND order_record.status = 'DELIVERED';