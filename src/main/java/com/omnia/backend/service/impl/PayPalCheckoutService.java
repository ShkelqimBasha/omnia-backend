package com.omnia.backend.service.impl;

import com.fasterxml.jackson.databind.*;
import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.interfaces.CheckoutService;
import com.omnia.backend.service.interfaces.OrderService;
import com.omnia.backend.enums.UserStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;

@Service
public class PayPalCheckoutService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PayPalCheckoutService.class);
    private final JdbcTemplate jdbc;
    private final CurrentUserService current;
    private final CheckoutService checkout;
    private final OrderService orders;
    private final PayPalGateway gateway;
    private final ObjectMapper json;
    private final EntityManager em;
    private final TransactionTemplate tx;
    public record View(String id,String state,String currency,BigDecimal amount,String approvalUrl,
                       String checkoutReference,List<Long> orderIds,boolean sandbox,String requestKey) {}
    private record Row(String id,long owner,String hash,long group,BigDecimal amount,String provider,
                       String approval,String state,Instant created) {}
    public PayPalCheckoutService(JdbcTemplate jdbc,CurrentUserService current,CheckoutService checkout,
                                 OrderService orders,PayPalGateway gateway,ObjectMapper json,EntityManager em,
                                 PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.current=current;this.checkout=checkout;this.orders=orders;
        this.gateway=gateway;this.json=json;this.em=em;this.tx=new TransactionTemplate(manager);
    }
    private long owner() {
        var user=current.requireCurrentUser();
        if(user.getStatus()!=UserStatus.ACTIVE) throw new AccessDeniedException("Active account required");
        return user.getId();
    }
    public View create(String key,CreateOrderRequest request) {
        gateway.requireReady(); uuid(key);long owner=owner();
        if(request.getItems()==null || request.getItems().size()>100) throw new IllegalArgumentException("Too many items");
        String hash=hash(request);
        String id=tx.execute(status->{
            jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,owner);
            Integer cancelled=jdbc.queryForObject("SELECT COUNT(*) FROM paypal_cancelled_requests WHERE user_id=? AND request_key=?",Integer.class,owner,key);
            if(cancelled!=null && cancelled>0)throw new IllegalArgumentException("This checkout request was cancelled");
            var existing=jdbc.query("SELECT * FROM paypal_checkouts WHERE user_id=? AND request_key=?",this::row,owner,key);
            if(!existing.isEmpty()) {
                Row r=existing.get(0);
                if(!r.hash.equals(hash))throw new IllegalArgumentException("Checkout key already used for different details");
                return r.id;
            }
            // One unresolved checkout per buyer prevents duplicate reservations across devices.
            Integer pending=jdbc.queryForObject("SELECT COUNT(*) FROM paypal_checkouts WHERE user_id=? AND state NOT IN ('PAID','REFUNDED','ABANDONED')",Integer.class,owner);
            if(pending!=null && pending>0) throw new IllegalArgumentException("Resume or cancel your existing PayPal checkout first");
            var result=checkout.checkout(request);em.flush();
            long group=jdbc.queryForObject("SELECT id FROM checkout_groups WHERE checkout_reference=? AND user_id=?",Long.class,result.getCheckoutReference(),owner);
            BigDecimal total=result.getTotalAmount();
            if(total==null || total.signum()<=0 || total.scale()>2)throw new IllegalArgumentException("PayPal amount must be positive EUR");
            String created=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO paypal_checkouts(id,user_id,request_key,request_hash,checkout_group_id,amount) VALUES(?,?,?,?,?,?)",created,owner,key,hash,group,total);
            jdbc.update("UPDATE payments p JOIN orders o ON o.id=p.order_id SET p.method='PAYPAL' WHERE o.checkout_group_id=? AND p.status='PENDING'",group);
            return created;
        });
        // Commit reservations before the network call. A timeout always retries this same provider request ID.
        return syncOwned(id,false,owner);
    }
    public View sync(String id,boolean capture) { uuid(id);return syncOwned(id,capture,owner()); }
    private View syncOwned(String id,boolean capture,long owner) {
        return tx.execute(status->{Row r=locked(id);if(r.owner!=owner)throw new AccessDeniedException("Payment not found");return reconcile(r,capture);});
    }
    public View latest() {
        long owner=owner();return tx.execute(s->{var rows=jdbc.query("SELECT * FROM paypal_checkouts WHERE user_id=? AND state NOT IN ('PAID','REFUNDED','ABANDONED') ORDER BY created_at DESC LIMIT 1",this::row,owner);return rows.isEmpty()?null:view(rows.get(0));});
    }
    public View forOrder(long orderId) {
        long owner=owner();return tx.execute(s->{var rows=jdbc.query("SELECT p.* FROM paypal_checkouts p JOIN orders o ON o.checkout_group_id=p.checkout_group_id WHERE o.id=? AND p.user_id=?",this::row,orderId,owner);
            if(rows.isEmpty()) throw new AccessDeniedException("Payment not found");return view(rows.get(0));});
    }
    public View abandon(String id) {
        uuid(id);long owner=owner();return tx.execute(s->{
            Row r=locked(id);if(r.owner!=owner)throw new AccessDeniedException("Payment not found");
            if("ABANDONED".equals(r.state))return view(r);
            if(r.provider!=null) {
                var proof=PayPalEvidence.verify(gateway.get(r.provider),r.provider,r.id,gateway.merchant(),r.amount);
                boolean refused=Set.of("EXHAUSTED","BLOCKED").contains(r.state) && "APPROVED".equals(proof.state())
                        && proof.captureId()==null;
                if(!refused && !Set.of("APPROVAL","FAILED").contains(proof.state()))throw new IllegalArgumentException("Payment approved or processing; check its status before cancellation");
            } else if(!"CREATING".equals(r.state))throw new IllegalArgumentException("Payment needs review");
            jdbc.update("UPDATE paypal_checkouts SET state='ABANDONED' WHERE id=?",id);
            // The attempt lock prevents late approval/webhooks from capturing after this point.
            for(long orderId:orderIds(r.group))orders.cancelMyOrder(orderId);
            return view(locked(id));
        });
    }
    public void cancelRequest(String key) {
        uuid(key);long owner=owner();tx.execute(s->{
            jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,owner);
            var ids=jdbc.queryForList("SELECT id FROM paypal_checkouts WHERE user_id=? AND request_key=?",String.class,owner,key);
            if(!ids.isEmpty())abandon(ids.get(0));
            jdbc.update("INSERT IGNORE INTO paypal_cancelled_requests(user_id,request_key) VALUES(?,?)",owner,key);
            return null;
        });
    }
    public void webhook(Map<String,String> headers,JsonNode event) {
        if(!gateway.verifyWebhook(headers,event))throw new AccessDeniedException("Invalid PayPal signature");
        String type=event.path("event_type").asText();
        String provider=type.startsWith("CHECKOUT.ORDER.")?event.path("resource").path("id").asText():
                event.path("resource").path("supplementary_data").path("related_ids").path("order_id").asText();
        var ids=jdbc.queryForList("SELECT id FROM paypal_checkouts WHERE paypal_order_id=?",String.class,provider);
        for(String id:ids)tx.execute(s->{
            Row r=locked(id);
            if("PAYMENT.CAPTURE.REVERSED".equals(type)) {
                JsonNode resource=event.path("resource");
                String capture=resource.path("id").asText();
                JsonNode amount=resource.path("amount");
                if(!"EUR".equals(amount.path("currency_code").asText()) || new BigDecimal(amount.path("value").asText()).compareTo(r.amount)!=0)
                    throw new IllegalArgumentException("Reversal amount mismatch");
                Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.checkout_group_id=? AND p.method='PAYPAL' AND p.transaction_id=?",Integer.class,r.group,capture);
                if(count==null || count!=orderIds(r.group).size())throw new IllegalArgumentException("Reversal capture mismatch");
                lockOrders(r);
                jdbc.update("UPDATE payments p JOIN orders o ON o.id=p.order_id SET p.status='FAILED' WHERE o.checkout_group_id=? AND p.method='PAYPAL'",r.group);
                jdbc.update("UPDATE paypal_checkouts SET state='REVIEW' WHERE id=?",r.id);
            } else reconcile(r,true);
            return null;
        });
    }
    private View reconcile(Row r,boolean capture) {
        if(Set.of("ABANDONED","REFUNDED","REVIEW").contains(r.state)) return view(r);
        gateway.requireReady();
        if(r.provider==null) {
            // PayPal's default idempotency retention is six hours. Never risk recreating an uncertain charge after it.
            if(r.created.plus(Duration.ofHours(5)).isBefore(Instant.now()))
                throw new IllegalArgumentException("Uncertain PayPal creation expired. Cancel these unpaid orders and start again.");
            JsonNode created=gateway.create(r.id,r.amount);
            String provider=created.path("id").asText();
            if(!provider.matches("[A-Z0-9]{5,64}"))throw new IllegalStateException("Invalid PayPal order response");
            String approval=PayPalGateway.approval(created);
            jdbc.update("UPDATE paypal_checkouts SET paypal_order_id=?,approval_url=?,state='APPROVAL' WHERE id=?",provider,approval,r.id);
            r=locked(r.id);
        }
        JsonNode details=gateway.get(r.provider);
        var proof=PayPalEvidence.verify(details,r.provider,r.id,gateway.merchant(),r.amount);
        boolean blocked=Set.of("BLOCKED","EXHAUSTED").contains(r.state);
        String refusalState=null;
        if(capture && !blocked && "APPROVED".equals(proof.state())) {
            // Serialize with all status changes; never capture a cancelled/changed local order.
            lockOrders(r);
            try { gateway.capture(r.provider,r.id); }
            catch(PayPalGateway.Rejected rejected) {
                if(!rejected.stopCapture())throw rejected;
                refusalState="MAX_NUMBER_OF_PAYMENT_ATTEMPTS_EXCEEDED".equals(rejected.issue)?"EXHAUSTED":"BLOCKED";
                // Persist before the follow-up GET: a failed GET must not enable another capture.
                jdbc.update("UPDATE paypal_checkouts SET state=? WHERE id=?",refusalState,r.id);
                log.warn("PayPal capture stopped checkout={} issue={} debug_id={}",r.id,rejected.issue,rejected.debugId);
            }
            catch(IllegalStateException uncertain) {
                // Includes timeout / ORDER_ALREADY_CAPTURED. Read provider truth before deciding anything.
            }
            try {
                proof=PayPalEvidence.verify(gateway.get(r.provider),r.provider,r.id,gateway.merchant(),r.amount);
            } catch(RuntimeException unavailable) {
                if(refusalState!=null)return view(locked(r.id));
                throw unavailable;
            }
        }
        if("PAID".equals(proof.state())) {
            lockOrders(r);
            // Record capture once; later identical evidence is harmless.
            jdbc.update("UPDATE payments p JOIN orders o ON o.id=p.order_id SET p.status='SUCCESS',p.transaction_id=?,p.paid_at=COALESCE(p.paid_at,?) WHERE o.checkout_group_id=? AND p.method='PAYPAL' AND p.status='PENDING'",proof.captureId(),java.sql.Timestamp.from(proof.paidAt()),r.group);
        } else if(Set.of("REFUNDED","REVIEW").contains(proof.state())) {
            lockOrders(r);
            // Partial refunds require review and are excluded from paid reports until reconciled manually.
            jdbc.update("UPDATE payments p JOIN orders o ON o.id=p.order_id SET p.status=? WHERE o.checkout_group_id=? AND p.method='PAYPAL'", "REFUNDED".equals(proof.state())?"REFUNDED":"PENDING",r.group);
        } else if("PAID".equals(r.state)) {
            // Never downgrade settled money because of a delayed event or an inconsistent response.
            return view(r);
        }
        String next=proof.state();
        // A GET saying APPROVED cannot erase a capture refusal. Settlement evidence still wins.
        if(Set.of("APPROVED","APPROVAL","PENDING").contains(next) && (blocked || refusalState!=null))
            next=refusalState!=null?refusalState:r.state;
        jdbc.update("UPDATE paypal_checkouts SET state=? WHERE id=?",next,r.id);
        return view(locked(r.id));
    }
    private void lockOrders(Row r) {
        var ids=jdbc.queryForList("SELECT id FROM orders WHERE checkout_group_id=? ORDER BY id FOR UPDATE",Long.class,r.group);
        if(ids.isEmpty())throw new IllegalStateException("Checkout orders missing");
        BigDecimal sum=jdbc.queryForObject("SELECT SUM(total_amount) FROM orders WHERE checkout_group_id=? AND status<>'CANCELLED'",BigDecimal.class,r.group);
        Long cancelled=jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE checkout_group_id=? AND status='CANCELLED'",Long.class,r.group);
        if(cancelled==null || cancelled!=0 || sum==null || sum.compareTo(r.amount)!=0)throw new IllegalStateException("Checkout changed; manual review required");
    }
    private List<Long> orderIds(long group) {return jdbc.queryForList("SELECT id FROM orders WHERE checkout_group_id=? ORDER BY id",Long.class,group);}
    private View view(Row r) {
        String ref=jdbc.queryForObject("SELECT checkout_reference FROM checkout_groups WHERE id=?",String.class,r.group);
        String key=jdbc.queryForObject("SELECT request_key FROM paypal_checkouts WHERE id=?",String.class,r.id);
        return new View(r.id,r.state,"EUR",r.amount,r.approval,ref,orderIds(r.group),true,key);
    }
    private Row locked(String id) {
        var rows=jdbc.query("SELECT * FROM paypal_checkouts WHERE id=? FOR UPDATE",this::row,id);
        if(rows.isEmpty())throw new AccessDeniedException("Payment not found");return rows.get(0);
    }
    private Row row(ResultSet r,int n)throws SQLException {return new Row(r.getString("id"),r.getLong("user_id"),r.getString("request_hash"),r.getLong("checkout_group_id"),r.getBigDecimal("amount"),r.getString("paypal_order_id"),r.getString("approval_url"),r.getString("state"),r.getTimestamp("created_at").toInstant());}
    private String hash(Object body) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalArgumentException("Invalid checkout request");}
    }
    private static void uuid(String id) {if(id==null || !UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException("Invalid checkout key");}
}
