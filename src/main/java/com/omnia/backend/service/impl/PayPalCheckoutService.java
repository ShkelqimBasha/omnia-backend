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
    private final SellerPayoutAdjustments adjustments;
    /** The only local states from which our backend may ask PayPal to capture. */
    static final Set<String> CAPTURABLE=Set.of("CREATING","APPROVAL","APPROVED","PENDING","FAILED");
    /** {@code sandbox} and {@code environment} describe the checkout's own environment (app contract). */
    public record View(String id,String state,String currency,BigDecimal amount,String approvalUrl,
                       String checkoutReference,List<Long> orderIds,boolean sandbox,String environment,String requestKey) {}
    /**
     * {@code attempted}: when a PayPal create request may first have been sent; null means none ever was.
     * {@code environment}: the PayPal environment the checkout was created in ("sandbox" or "live").
     */
    private record Row(String id,long owner,String hash,long group,BigDecimal amount,String provider,
                       String approval,String state,Instant created,Instant attempted,String environment) {}
    public PayPalCheckoutService(JdbcTemplate jdbc,CurrentUserService current,CheckoutService checkout,
                                 PayPalRecoveryTransitions transitions,PayPalGateway gateway,ObjectMapper json,EntityManager em,
                                 PlatformTransactionManager manager,PayPalRecoveryLedger ledger,
                                 SellerPayoutAdjustments adjustments) {
        this.jdbc=jdbc;this.current=current;this.checkout=checkout;this.transitions=transitions;
        this.gateway=gateway;this.json=json;this.em=em;this.tx=new TransactionTemplate(manager);this.ledger=ledger;this.adjustments=adjustments;
    }
    private long owner() {
        var user=current.requireCurrentUser();
        if(user.getStatus()!=UserStatus.ACTIVE) throw new AccessDeniedException("Active account required");
        return user.getId();
    }
    public View create(String key,CreateOrderRequest request) { return create(key,request,false); }
    /** {@code web}: the buyer started on the web app, so PayPal returns them there (see PayPalGateway). */
    public View create(String key,CreateOrderRequest request,boolean web) {
        gateway.requireReady(); uuid(key);long owner=owner();
        if(request.getItems()==null || request.getItems().size()>100) throw new IllegalArgumentException("Too many items");
        String hash=hash(request);
        String environment=gateway.environment();
        if(environment==null)throw new IllegalStateException("PayPal is not configured");
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
            // One unresolved checkout per buyer prevents duplicate reservations across devices. A checkout of
            // the other environment cannot be resumed or checked here, so it does not block a new purchase.
            Integer pending=jdbc.queryForObject("SELECT COUNT(*) FROM paypal_checkouts WHERE user_id=? AND environment=? AND state NOT IN ('PAID','REFUNDED','ABANDONED','RECOVERY_REQUIRED')",Integer.class,owner,environment);
            if(pending!=null && pending>0) throw new IllegalArgumentException("Resume or cancel your existing PayPal checkout first");
            var result=checkout.checkout(request,com.omnia.backend.enums.PaymentMethod.PAYPAL);em.flush();
            long group=jdbc.queryForObject("SELECT id FROM checkout_groups WHERE checkout_reference=? AND user_id=?",Long.class,result.getCheckoutReference(),owner);
            BigDecimal total=result.getTotalAmount();
            if(total==null || total.signum()<=0 || total.scale()>2)throw new IllegalArgumentException("PayPal amount must be positive EUR");
            String created=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO paypal_checkouts(id,user_id,request_key,request_hash,checkout_group_id,amount,environment) VALUES(?,?,?,?,?,?,?)",created,owner,key,hash,group,total,environment);
            jdbc.update("UPDATE payments p JOIN orders o ON o.id=p.order_id SET p.method='PAYPAL' WHERE o.checkout_group_id=? AND p.status='PENDING'",group);
            return created;
        });
        // Commit reservations before the network call. A timeout always retries this same provider request ID.
        return syncOwned(id,false,owner,web);
    }
    public View sync(String id,boolean capture) { uuid(id);return syncOwned(id,capture,owner(),false); }
    private View syncOwned(String id,boolean capture,long owner,boolean web) {
        // Commit the attempt marker before reconcile() may send a create request. If PayPal creates the
        // order but the response or the local commit is lost, the marker survives the rollback, so a
        // missing PayPal order ID is never mistaken for proof that no PayPal order exists.
        // Only a checkout of this server's environment can be sent to PayPal, so only it is marked.
        tx.executeWithoutResult(status->jdbc.update("UPDATE paypal_checkouts SET paypal_create_attempted_at=UTC_TIMESTAMP(6) WHERE id=? AND user_id=? AND environment=? AND paypal_order_id IS NULL AND state='CREATING' AND paypal_create_attempted_at IS NULL AND created_at>=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 5 HOUR)",id,owner,gateway.environment()));
        return tx.execute(status->{Row r=locked(id);if(r.owner!=owner)throw new AccessDeniedException("Payment not found");if("RECOVERY_REQUIRED".equals(r.state))return view(r);return reconcile(r,capture,web);});
    }
    public View latest() {
        // The open checkout of this server's environment (any environment while PayPal is not configured).
        long owner=owner();String environment=gateway.environment();
        return tx.execute(s->{var rows=jdbc.query("SELECT * FROM paypal_checkouts WHERE user_id=? AND environment=COALESCE(?,environment) AND state NOT IN ('PAID','REFUNDED','ABANDONED','RECOVERY_REQUIRED') ORDER BY created_at DESC LIMIT 1",this::row,owner,environment);return rows.isEmpty()?null:view(rows.get(0));});
    }
    public View forOrder(long orderId) {
        long owner=owner();return tx.execute(s->{var rows=jdbc.query("SELECT p.* FROM paypal_checkouts p JOIN orders o ON o.checkout_group_id=p.checkout_group_id WHERE o.id=? AND p.user_id=?",this::row,orderId,owner);
            if(rows.isEmpty()) throw new AccessDeniedException("Payment not found");return view(rows.get(0));});
    }
    public View abandon(String id) {
        uuid(id);long owner=owner();return tx.execute(s->{
            // Same global order as creation and recovery: the buyer's users row before the checkout row.
            transitions.lockUsers(List.of(owner));
            Row r=locked(id);if(r.owner!=owner)throw new AccessDeniedException("Payment not found");
            if("RECOVERY_REQUIRED".equals(r.state))throw new IllegalArgumentException("Payment needs manual review"); if("ABANDONED".equals(r.state))return view(r);
            PayPalRecoveryEvidence.Snapshot evidence;
            if(r.provider!=null) {
                requireServed(r);
                JsonNode details=gateway.get(r.provider);
                String merchant=gateway.merchant();
                var proof=PayPalEvidence.verify(details,r.provider,r.id,merchant,r.amount);
                boolean refused=Set.of("EXHAUSTED","BLOCKED").contains(r.state) && "APPROVED".equals(proof.state())
                        && proof.captureId()==null;
                if(!refused && !Set.of("APPROVAL","FAILED").contains(proof.state()))throw new IllegalArgumentException("Payment approved or processing; check its status before cancellation");
                evidence=PayPalRecoveryEvidence.from(details,r.provider,r.id,merchant,r.amount);
            } else if(!"CREATING".equals(r.state))throw new IllegalArgumentException("Payment needs review");
            // A create request may have succeeded at PayPal even though its order ID never reached us.
            // Checking the status retries the same idempotent request and records the real order first.
            else if(r.attempted!=null)throw new IllegalArgumentException("PayPal may already have this payment; check its status before cancellation");
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
        // The event was verified with this server's credentials, so it can only concern this environment's
        // checkouts; a checkout of the other environment is never touched by it.
        var ids=jdbc.queryForList("SELECT id FROM paypal_checkouts WHERE paypal_order_id=? AND environment=?",String.class,provider,gateway.environment());
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
        if("REVIEW".equals(r.state)) return refundUnderReview(r);
        reconcile(r,true,false);
        return "RECONCILED";
    }
    /** Provider truth for a webhook. Missing evidence is never read as "unpaid": PayPal redelivers. */
    private PayPalRecoveryEvidence.Snapshot verifiedEvidence(Row r) {
        var evidence=PayPalRecoveryEvidence.fetch(gateway,r.environment,r.provider,r.attempted!=null,r.id,r.amount);
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
    /**
     * A partly refunded (REVIEW) checkout is never reconciled again, but a further refund of orders already
     * paid out to their sellers must still reach the payout adjustments. Nothing else changes here.
     */
    private String refundUnderReview(Row r) {
        Integer paidOut=jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE checkout_group_id=? AND payout_id IS NOT NULL",Integer.class,r.group);
        if(paidOut==null || paidOut==0 || r.provider==null) return "RECONCILED";
        gateway.requireReady();
        requireServed(r);
        JsonNode details=gateway.get(r.provider);
        var proof=PayPalEvidence.verify(details,r.provider,r.id,gateway.merchant(),r.amount);
        if(!Set.of("REFUNDED","REVIEW").contains(proof.state())) return "RECONCILED";
        try { transitions.lockGroup(r.id,r.group); }
        catch(com.omnia.backend.common.exception.ConflictException changed) { return "RECONCILED"; }
        return recordRefund(r,proof.state(),details)>0?"PAYOUT_ADJUSTED":"RECONCILED";
    }
    /** Seller payout adjustments for a verified (partial) refund; 0 when the refunded amount is unknown. */
    private int recordRefund(Row r,String state,JsonNode details) {
        boolean full="REFUNDED".equals(state);
        BigDecimal refunded=full?r.amount:PayPalEvidence.refundedTotal(details,r.amount);
        if(refunded==null) {
            log.warn("PayPal partial refund amount unreadable checkout={}; payout adjustments need manual review",r.id);
            return 0;
        }
        return adjustments.recordRefund(r.group,refunded,full?SellerPayoutAdjustments.SOURCE_REFUND:SellerPayoutAdjustments.SOURCE_PARTIAL_REFUND);
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
        List<Long> locked=lockOrders(r);
        jdbc.update("UPDATE payments SET status='FAILED' WHERE order_id IN ("+marks(locked)+") AND method='PAYPAL'",locked.toArray());
        jdbc.update("UPDATE paypal_checkouts SET state='REVIEW' WHERE id=?",r.id);
        // PayPal took the whole payment back: sellers already paid out for these orders owe it to Omnia.
        adjustments.recordRefund(r.group,r.amount,SellerPayoutAdjustments.SOURCE_REVERSAL);
        if(ledger.openCase(r.id,"REVERSAL",captureKey,null,"Capture reversed after settlement")) {
            ledger.event(r.id,"REVERSAL",null,r.state,"REVIEW","Recorded from a verified PayPal webhook",null,"capture="+captureKey);
            ledger.alertSuperAdmins(r.id,"REVERSAL",captureKey,r.amount);
        }
        return "REVERSED";
    }
    private View reconcile(Row r,boolean capture,boolean web) {
        if(Set.of("ABANDONED","REFUNDED","REVIEW","RECOVERY_REQUIRED").contains(r.state)) return view(r);
        gateway.requireReady();
        requireServed(r);
        if(r.provider==null) {
            // Every create request must follow a committed attempt marker (see syncOwned).
            if(r.attempted==null)
                throw new IllegalArgumentException("PayPal checkout expired before it reached PayPal. Cancel these unpaid orders and start again.");
            // PayPal's default idempotency retention is six hours. Never risk recreating an uncertain charge after it:
            // the earlier request may have created an order, so hand the checkout to SUPER_ADMIN recovery instead.
            if(r.created.plus(Duration.ofHours(5)).isBefore(Instant.now())) return uncertainCreation(r);
            JsonNode created=gateway.create(r.id,r.amount,web);
            String provider=created.path("id").asText();
            if(!provider.matches("[A-Z0-9]{5,64}"))throw new IllegalStateException("Invalid PayPal order response");
            String approval=PayPalGateway.approval(created,r.environment);
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
                details=gateway.get(r.provider);
                proof=PayPalEvidence.verify(details,r.provider,r.id,gateway.merchant(),r.amount);
            } catch(RuntimeException unavailable) {
                if(refusalState!=null)return view(locked(r.id));
                throw unavailable;
            }
        }
        if("PAID".equals(proof.state())) {
            List<Long> locked=lockOrders(r);
            // Record capture once; later identical evidence is harmless.
            List<Object> args=new ArrayList<>(List.of(proof.captureId(),java.sql.Timestamp.from(proof.paidAt())));args.addAll(locked);
            jdbc.update("UPDATE payments SET status='SUCCESS',transaction_id=?,paid_at=COALESCE(paid_at,?) WHERE order_id IN ("+marks(locked)+") AND method='PAYPAL' AND status='PENDING'",args.toArray());
        } else if(Set.of("REFUNDED","REVIEW").contains(proof.state())) {
            List<Long> locked=lockOrders(r);
            // Partial refunds require review and are excluded from paid reports until reconciled manually.
            List<Object> args=new ArrayList<>(List.of("REFUNDED".equals(proof.state())?"REFUNDED":"PENDING"));args.addAll(locked);
            jdbc.update("UPDATE payments SET status=? WHERE order_id IN ("+marks(locked)+") AND method='PAYPAL'",args.toArray());
            // Orders already paid out to their sellers: record what each seller owes back.
            recordRefund(r,proof.state(),details);
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
    /**
     * The create request may have succeeded at PayPal, but its order ID is unknown and PayPal no longer
     * replays the request. Nothing is cancelled and no stock moves: the checkout leaves the buyer's
     * active list and waits in RECOVERY_REQUIRED, where only verified evidence can resolve it.
     */
    private View uncertainCreation(Row r) {
        int moved=jdbc.update("UPDATE paypal_checkouts SET state='RECOVERY_REQUIRED' WHERE id=? AND state='CREATING' AND paypal_order_id IS NULL",r.id);
        if(moved==1) ledger.event(r.id,"CREATION_UNCERTAIN",null,r.state,"RECOVERY_REQUIRED",
                "PayPal creation could not be confirmed within PayPal's idempotency window",PayPalRecoveryEvidence.creationUncertain(),null);
        return view(locked(r.id));
    }
    /**
     * Locks the group's orders by primary key (record locks only; see PayPalRecoveryTransitions.lockGroup)
     * and returns their IDs. Any change to the group refuses capture or settlement.
     */
    private List<Long> lockOrders(Row r) {
        List<PayPalRecoveryTransitions.GroupOrder> orders;
        try { orders=transitions.lockGroup(r.id,r.group); }
        catch(com.omnia.backend.common.exception.ConflictException changed) { throw new IllegalStateException("Checkout changed; manual review required"); }
        if(PayPalRecoveryTransitions.anyCancelled(orders) || !PayPalRecoveryTransitions.totalsMatch(orders,r.amount))
            throw new IllegalStateException("Checkout changed; manual review required");
        return orders.stream().map(PayPalRecoveryTransitions.GroupOrder::id).toList();
    }
    /**
     * A checkout is only ever sent to the PayPal environment it was created in. A Sandbox checkout on a
     * Live server (or the reverse) is refused before any request; nothing about it changes.
     */
    private void requireServed(Row r) {
        if(!PayPalGateway.serves(gateway.environment(),r.environment))
            throw new IllegalStateException("This PayPal checkout belongs to PayPal "+r.environment+" and cannot be processed by this server");
    }
    private static String marks(List<?> values) {return String.join(",",Collections.nCopies(values.size(),"?"));}
    private List<Long> orderIds(long group) {return jdbc.queryForList("SELECT id FROM orders WHERE checkout_group_id=? ORDER BY id",Long.class,group);}
    private View view(Row r) {
        String ref=jdbc.queryForObject("SELECT checkout_reference FROM checkout_groups WHERE id=?",String.class,r.group);
        String key=jdbc.queryForObject("SELECT request_key FROM paypal_checkouts WHERE id=?",String.class,r.id);
        return new View(r.id,r.state,"EUR",r.amount,r.approval,ref,orderIds(r.group),!PayPalGateway.LIVE.equals(r.environment),r.environment,key);
    }
    private Row locked(String id) {
        var rows=jdbc.query("SELECT * FROM paypal_checkouts WHERE id=? FOR UPDATE",this::row,id);
        if(rows.isEmpty())throw new AccessDeniedException("Payment not found");return rows.get(0);
    }
    private Row row(ResultSet r,int n)throws SQLException {return new Row(r.getString("id"),r.getLong("user_id"),r.getString("request_hash"),r.getLong("checkout_group_id"),r.getBigDecimal("amount"),r.getString("paypal_order_id"),r.getString("approval_url"),r.getString("state"),r.getTimestamp("created_at").toInstant(),
            Optional.ofNullable(r.getTimestamp("paypal_create_attempted_at")).map(java.sql.Timestamp::toInstant).orElse(null),
            r.getString("environment"));}
    private String hash(Object body) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalArgumentException("Invalid checkout request");}
    }
    private static void uuid(String id) {if(id==null || !UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException("Invalid checkout key");}
}
