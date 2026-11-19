package com.omnia.backend.service.impl;

import com.fasterxml.jackson.databind.*;
import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.interfaces.CheckoutService;
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
    private final PayPalRecoveryTransitions transitions;
    private final PayPalGateway gateway;
    private final ObjectMapper json;
    private final EntityManager em;
    private final TransactionTemplate tx;
    private final PayPalRecoveryLedger ledger;
    /** The only local states from which our backend may ask PayPal to capture. */
    static final Set<String> CAPTURABLE=Set.of("CREATING","APPROVAL","APPROVED","PENDING","FAILED");
    public record View(String id,String state,String currency,BigDecimal amount,String approvalUrl,
                       String checkoutReference,List<Long> orderIds,boolean sandbox,String requestKey) {}
    private record Row(String id,long owner,String hash,long group,BigDecimal amount,String provider,
                       String approval,String state,Instant created) {}
    public PayPalCheckoutService(JdbcTemplate jdbc,CurrentUserService current,CheckoutService checkout,
                                 PayPalRecoveryTransitions transitions,PayPalGateway gateway,ObjectMapper json,EntityManager em,
                                 PlatformTransactionManager manager,PayPalRecoveryLedger ledger) {
        this.jdbc=jdbc;this.current=current;this.checkout=checkout;this.transitions=transitions;
        this.gateway=gateway;this.json=json;this.em=em;this.tx=new TransactionTemplate(manager);this.ledger=ledger;
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
            Integer pending=jdbc.queryForObject("SELECT COUNT(*) FROM paypal_checkouts WHERE user_id=? AND state NOT IN ('PAID','REFUNDED','ABANDONED','RECOVERY_REQUIRED')",Integer.class,owner);
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
        return tx.execute(status->{Row r=locked(id);if(r.owner!=owner)throw new AccessDeniedException("Payment not found");if("RECOVERY_REQUIRED".equals(r.state))return view(r);return reconcile(r,capture);});
    }
    public View latest() {
        long owner=owner();return tx.execute(s->{var rows=jdbc.query("SELECT * FROM paypal_checkouts WHERE user_id=? AND state NOT IN ('PAID','REFUNDED','ABANDONED','RECOVERY_REQUIRED') ORDER BY created_at DESC LIMIT 1",this::row,owner);return rows.isEmpty()?null:view(rows.get(0));});
    }
    public View forOrder(long orderId) {
        long owner=owner();return tx.execute(s->{var rows=jdbc.query("SELECT p.* FROM paypal_checkouts p JOIN orders o ON o.checkout_group_id=p.checkout_group_id WHERE o.id=? AND p.user_id=?",this::row,orderId,owner);
            if(rows.isEmpty()) throw new AccessDeniedException("Payment not found");return view(rows.get(0));});
    }
    public View abandon(String id) {
        uuid(id);long owner=owner();return tx.execute(s->{
            Row r=locked(id);if(r.owner!=owner)throw new AccessDeniedException("Payment not found");
            if("RECOVERY_REQUIRED".equals(r.state))throw new IllegalArgumentException("Payment needs manual review"); if("ABANDONED".equals(r.state))return view(r);
            PayPalRecoveryEvidence.Snapshot evidence;
            if(r.provider!=null) {
                JsonNode details=gateway.get(r.provider);
                String merchant=gateway.merchant();
                var proof=PayPalEvidence.verify(details,r.provider,r.id,merchant,r.amount);
                boolean refused=Set.of("EXHAUSTED","BLOCKED").contains(r.state) && "APPROVED".equals(proof.state())
                        && proof.captureId()==null;
                if(!refused && !Set.of("APPROVAL","FAILED").contains(proof.state()))throw new IllegalArgumentException("Payment approved or processing; check its status before cancellation");
                evidence=PayPalRecoveryEvidence.from(details,r.provider,r.id,merchant,r.amount);
            } else if(!"CREATING".equals(r.state))throw new IllegalArgumentException("Payment needs review");
            else evidence=PayPalRecoveryEvidence.notCreated();
            // One statement set cancels every order of the group, restores stock once (products locked in ID
            // order), fails the PayPal payments, marks the checkout ABANDONED and audits it. ABANDONED is
            // outside CAPTURABLE, so no later check, capture or webhook can capture this checkout.
            transitions.cancelGroup(r.id,r.group,r.state,"ABANDONED","FAILED",evidence,owner,
                    "Buyer cancelled unpaid PayPal orders","BUYER_ABANDONED");
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
        String eventId=event.path("id").asText("");
        String delivery=eventId.matches("[A-Za-z0-9-]{1,64}")?eventId:null;
        var ids=jdbc.queryForList("SELECT id FROM paypal_checkouts WHERE paypal_order_id=?",String.class,provider);
        for(String id:ids)tx.execute(s->{
            Row r=locked(id);
            // The same PayPal delivery again (same event ID): nothing to do.
            if(delivery!=null && ledger.webhookSeen(delivery,r.id))return null;
            String outcome=handleWebhook(r,type,event);
            if(delivery!=null)ledger.webhookRecord(delivery,r.id,type,outcome);
            return null;
        });
    }
    private String handleWebhook(Row r,String type,JsonNode event) {
        // Under recovery, an admin decides from evidence; webhooks only record what PayPal verifies.
        if("RECOVERY_REQUIRED".equals(r.state)) return recoveryEvidence(r);
        // Closed checkouts never capture again; verified money after closing opens a review case.
        if(Set.of("ABANDONED","REFUNDED").contains(r.state)) return closedCheckoutEvidence(r);
        if("PAYMENT.CAPTURE.REVERSED".equals(type)) return reversal(r,event);
        reconcile(r,true);
        return "RECONCILED";
    }
    /** Provider truth for a webhook. Missing evidence is never read as "unpaid": PayPal redelivers. */
    private PayPalRecoveryEvidence.Snapshot verifiedEvidence(Row r) {
        var evidence=PayPalRecoveryEvidence.fetch(gateway,r.provider,r.id,r.amount);
        if(!evidence.available() && !"MISMATCH".equals(evidence.availability()))
            throw new IllegalStateException("PayPal evidence unavailable; retry");
        return evidence;
    }
    private List<String> recordedCaptures(Row r) {
        return jdbc.queryForList("SELECT DISTINCT p.transaction_id FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.checkout_group_id=? AND p.method='PAYPAL' AND p.transaction_id IS NOT NULL",String.class,r.group);
    }
    private String reviewCase(Row r,String kind,String captureKey,PayPalRecoveryEvidence.Snapshot evidence,String details) {
        if(!ledger.openCase(r.id,kind,captureKey,evidence,details)) return "DUPLICATE_"+kind;
        ledger.event(r.id,kind,null,r.state,r.state,"Recorded from a verified PayPal webhook",evidence,details);
        ledger.alertSuperAdmins(r.id,kind,captureKey,r.amount);
        return kind;
    }
    private String recoveryEvidence(Row r) {
        var evidence=verifiedEvidence(r);
        if("MISMATCH".equals(evidence.availability()))return reviewCase(r,"EVIDENCE_MISMATCH","",evidence,"Provider order does not match this checkout");
        if(evidence.captureId()==null && Set.of("APPROVAL","APPROVED").contains(evidence.outcome())) return "RECOVERY_UNCHANGED";
        String key=evidence.outcome()+":"+(evidence.captureId()==null?"":evidence.captureId());
        return reviewCase(r,"RECOVERY_EVIDENCE",key,evidence,"Verified PayPal evidence for a checkout under recovery");
    }
    private String closedCheckoutEvidence(Row r) {
        var evidence=verifiedEvidence(r);
        if("MISMATCH".equals(evidence.availability()))return reviewCase(r,"EVIDENCE_MISMATCH","",evidence,"Provider order does not match this checkout");
        // No money moved (no capture, or the capture was declined/failed).
        if(evidence.captureId()==null || "FAILED".equals(evidence.outcome())) return "NO_CAPTURE";
        // The refund we already recorded, reported again: an idempotent no-op.
        if("REFUNDED".equals(r.state) && "REFUNDED".equals(evidence.outcome()) && recordedCaptures(r).contains(evidence.captureId()))
            return "DUPLICATE_REFUND";
        String kind="ABANDONED".equals(r.state)?"LATE_CAPTURE":"CAPTURE_CONFLICT";
        return reviewCase(r,kind,evidence.captureId(),evidence,"Verified capture "+evidence.captureStatus()+" for a "+r.state+" checkout");
    }
    private String reversal(Row r,JsonNode event) {
        JsonNode resource=event.path("resource");
        String capture=resource.path("id").asText("");
        String captureKey=capture.matches("[A-Z0-9]{5,64}")?capture:"";
        JsonNode amount=resource.path("amount");
        boolean amountMatches;
        try { amountMatches="EUR".equals(amount.path("currency_code").asText()) && new BigDecimal(amount.path("value").asText()).compareTo(r.amount)==0; }
        catch(NumberFormatException invalid) { amountMatches=false; }
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.checkout_group_id=? AND p.method='PAYPAL' AND p.transaction_id=?",Integer.class,r.group,capture);
        if(!amountMatches || captureKey.isEmpty() || count==null || count!=orderIds(r.group).size())
            return reviewCase(r,"REVERSAL_CONFLICT",captureKey,verifiedEvidence(r),"Reversal does not match the recorded capture");
        Integer open=jdbc.queryForObject("SELECT COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.checkout_group_id=? AND p.method='PAYPAL' AND p.status<>'FAILED'",Integer.class,r.group);
        if("REVIEW".equals(r.state) && open!=null && open==0) return "DUPLICATE_REVERSAL";
        lockOrders(r);
        jdbc.update("UPDATE payments p JOIN orders o ON o.id=p.order_id SET p.status='FAILED' WHERE o.checkout_group_id=? AND p.method='PAYPAL'",r.group);
        jdbc.update("UPDATE paypal_checkouts SET state='REVIEW' WHERE id=?",r.id);
        if(ledger.openCase(r.id,"REVERSAL",captureKey,null,"Capture reversed after settlement")) {
            ledger.event(r.id,"REVERSAL",null,r.state,"REVIEW","Recorded from a verified PayPal webhook",null,"capture="+captureKey);
            ledger.alertSuperAdmins(r.id,"REVERSAL",captureKey,r.amount);
        }
        return "REVERSED";
    }
    private View reconcile(Row r,boolean capture) {
        if(Set.of("ABANDONED","REFUNDED","REVIEW","RECOVERY_REQUIRED").contains(r.state)) return view(r);
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
            // Defence in depth: abandoned, refunded, settled or reviewed checkouts are never captured.
            if(!CAPTURABLE.contains(r.state)) throw new IllegalStateException("Checkout is not capturable");
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
