package com.omnia.backend.service.impl;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.omnia.backend.entity.Order;

@Service
public class PayPalOrderGuard {
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    public PayPalOrderGuard(JdbcTemplate jdbc, EntityManager em) { this.jdbc=jdbc;this.em=em; }
    /** Serialize every order mutation against capture and abandonment, in the same lock order. */
    public void check(Order order, boolean cancelling) {
        var ids=jdbc.queryForList("SELECT p.id FROM paypal_checkouts p JOIN orders o ON o.checkout_group_id=p.checkout_group_id WHERE o.id=?",String.class,order.getId());
        if(ids.isEmpty()) return;
        String state=jdbc.queryForObject("SELECT state FROM paypal_checkouts WHERE id=? FOR UPDATE",String.class,ids.get(0));
        jdbc.queryForObject("SELECT id FROM orders WHERE id=? FOR UPDATE",Long.class,order.getId());
        em.refresh(order);
        if(cancelling ? !("ABANDONED".equals(state)||"REFUNDED".equals(state)) : !"PAID".equals(state))
            throw new IllegalArgumentException(cancelling
                    ? "Cancel unpaid PayPal orders from the payment screen. Paid orders require a refund before cancellation."
                    : "PayPal payment must be confirmed before processing this order.");
    }
}
