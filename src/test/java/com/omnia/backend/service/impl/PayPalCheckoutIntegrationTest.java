package com.omnia.backend.service.impl;

import com.omnia.backend.integration.AbstractIntegrationTest;
import com.omnia.backend.entity.User;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.Order;
import com.omnia.backend.entity.Payment;
import com.omnia.backend.entity.CheckoutGroup;
import com.omnia.backend.enums.*;
import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.dto.response.CheckoutResponse;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.interfaces.CheckoutService;
import com.omnia.backend.service.interfaces.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Transactional
class PayPalCheckoutIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;@Autowired EntityManager em;@Autowired PlatformTransactionManager manager;
    @Autowired PayPalOrderGuard guard;@Autowired PayPalRecoveryLedger ledger;@Autowired PayPalRecoveryTransitions transitions;
    private PayPalCheckoutService service;private PayPalGateway gateway;private CurrentUserService current;
    private OrderService orderService;private CheckoutService checkout;private User user;private CheckoutGroup group;private Order order;private String key;
    private final ObjectMapper json=new ObjectMapper();private String attempt;
    @BeforeEach void setup()throws Exception{
        gateway=mock(PayPalGateway.class);current=mock(CurrentUserService.class);checkout=mock(CheckoutService.class);
        when(gateway.environment()).thenReturn("sandbox");
        when(gateway.merchant()).thenReturn("MERCHANT1");
        var roles=em.createQuery("select r from Role r where r.name='USER'",Role.class).getResultList();
        Role role=roles.isEmpty()?Role.builder().name("USER").build():roles.get(0);if(roles.isEmpty())em.persist(role);
        String name="pp30"+UUID.randomUUID().toString().replace("-","");
        user=User.builder().username(name).email(name+"@example.test").passwordHash("unused").firstName("Test").role(role).status(UserStatus.ACTIVE).build();em.persist(user);
        group=CheckoutGroup.builder().user(user).totalAmount(new BigDecimal("12.30")).orderCount(1).build();em.persist(group);
        order=Order.builder().user(user).checkoutGroup(group).status(OrderStatus.PENDING).totalAmount(new BigDecimal("12.30")).build();em.persist(order);
        em.persist(Payment.builder().order(order).method(PaymentMethod.CASH_ON_DELIVERY).status(PaymentStatus.PENDING).build());em.flush();
        when(current.requireCurrentUser()).thenReturn(user);
        when(checkout.checkout(any())).thenReturn(CheckoutResponse.builder().checkoutReference(group.getCheckoutReference()).totalAmount(new BigDecimal("12.30")).build());
        orderService=mock(OrderService.class);service=new PayPalCheckoutService(jdbc,current,checkout,transitions,gateway,json,em,manager,ledger);key=UUID.randomUUID().toString();
        when(gateway.create(anyString(),any())).thenAnswer(inv->{attempt=inv.getArgument(0);return json.readTree("{\"id\":\"PROVIDER123\",\"links\":[{\"rel\":\"approve\",\"href\":\"https://www.sandbox.paypal.com/checkoutnow?token=PROVIDER123\"}]}");});
        when(gateway.get("PROVIDER123")).thenAnswer(inv->evidence("CREATED",null));
    }
    private CreateOrderRequest request(){return CreateOrderRequest.builder().items(List.of()).build();}
    private JsonNode evidence(String status,String capture)throws Exception{
        var n=json.createObjectNode().put("id","PROVIDER123").put("intent","CAPTURE").put("status",status);
        var u=n.putArray("purchase_units").addObject().put("reference_id",attempt);
        u.putObject("payee").put("merchant_id","MERCHANT1");u.putObject("amount").put("currency_code","EUR").put("value",group.getTotalAmount().toPlainString());
        if(capture!=null){var c=u.putObject("payments").putArray("captures").addObject().put("id","CAPTURE123").put("status",capture).put("final_capture",true).put("create_time","2026-11-02T10:00:00Z");c.putObject("amount").put("currency_code","EUR").put("value",group.getTotalAmount().toPlainString());}
        return n;
    }
    @Test void retryUsesSameOrdersAndProviderRequest()throws Exception{
        var first=service.create(key,request());var second=service.create(key,request());
        assertEquals(first.id(),second.id());verify(checkout,times(1)).checkout(any());verify(gateway,times(1)).create(anyString(),any());
        assertEquals("PAYPAL",jdbc.queryForObject("SELECT method FROM payments WHERE order_id=?",String.class,order.getId()));
        assertThrows(IllegalArgumentException.class,()->guard.check(order,false));
    }
    @Test void completedCaptureSetsEveryOrderPaymentOnce()throws Exception{
        var first=service.create(key,request());
        when(gateway.get("PROVIDER123")).thenReturn(evidence("APPROVED",null),evidence("COMPLETED","COMPLETED"),evidence("COMPLETED","COMPLETED"));
        assertEquals("PAID",service.sync(first.id(),true).state());assertEquals("PAID",service.sync(first.id(),true).state());
        verify(gateway,times(1)).capture("PROVIDER123",first.id());
        assertEquals("SUCCESS",jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,order.getId()));
        assertNotNull(jdbc.queryForObject("SELECT paid_at FROM payments WHERE order_id=?",java.sql.Timestamp.class,order.getId()));
        guard.check(order,false);assertThrows(IllegalArgumentException.class,()->guard.check(order,true));
    }
    @Test void oneCapturePaysAllCompanyOrders()throws Exception{
        Order second=Order.builder().user(user).checkoutGroup(group).status(OrderStatus.PENDING).totalAmount(new BigDecimal("12.30")).build();em.persist(second);
        em.persist(Payment.builder().order(second).method(PaymentMethod.CASH_ON_DELIVERY).status(PaymentStatus.PENDING).build());
        group.setTotalAmount(new BigDecimal("24.60"));em.flush();
        when(checkout.checkout(any())).thenReturn(CheckoutResponse.builder().checkoutReference(group.getCheckoutReference()).totalAmount(new BigDecimal("24.60")).build());
        var first=service.create(key,request());when(gateway.get("PROVIDER123")).thenReturn(evidence("APPROVED",null),evidence("COMPLETED","COMPLETED"));
        assertEquals("PAID",service.sync(first.id(),true).state());
        assertEquals(2L,jdbc.queryForObject("SELECT COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.checkout_group_id=? AND p.status='SUCCESS'",Long.class,group.getId()));
    }
    @Test void cancelledRequestKeyCannotCreateOrdersLater(){
        service.cancelRequest(key);assertThrows(IllegalArgumentException.class,()->service.create(key,request()));
        verify(checkout,never()).checkout(any());verify(gateway,never()).create(anyString(),any());
    }
    @Test void uncertainCaptureResponseReadsProviderTruthBeforeSettling()throws Exception{
        var first=service.create(key,request());
        when(gateway.get("PROVIDER123")).thenReturn(evidence("APPROVED",null),evidence("COMPLETED","COMPLETED"));
        when(gateway.capture("PROVIDER123",first.id())).thenThrow(new IllegalStateException("timeout"));
        assertEquals("PAID",service.sync(first.id(),true).state());
        assertEquals("SUCCESS",jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,order.getId()));
    }
    @Test void anotherBuyerCannotReadCaptureOrCancel()throws Exception{
        var first=service.create(key,request());User other=User.builder().id(user.getId()+100000).status(UserStatus.ACTIVE).build();when(current.requireCurrentUser()).thenReturn(other);
        assertThrows(AccessDeniedException.class,()->service.sync(first.id(),true));
        // Each denied transaction marks the test transaction rollback-only; no provider call is allowed.
        verify(gateway,never()).capture(anyString(),anyString());
    }
    @Test void approvedPaymentCannotBeAbandoned()throws Exception{
        var first=service.create(key,request());when(gateway.get("PROVIDER123")).thenReturn(evidence("APPROVED",null));
        assertThrows(IllegalArgumentException.class,()->service.abandon(first.id()));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,order.getId()));
    }
    @Test void invalidWebhookCannotMutatePayment()throws Exception{
        service.create(key,request());when(gateway.verifyWebhook(anyMap(),any())).thenReturn(false);
        assertThrows(AccessDeniedException.class,()->service.webhook(Map.of(),json.createObjectNode()));
        verify(gateway,never()).capture(anyString(),anyString());
    }

    @Test void exhaustedCaptureStopsRetriesAndAllowsCancellationAfterFreshProof()throws Exception{
        var first=service.create(key,request());
        when(gateway.get("PROVIDER123")).thenReturn(evidence("APPROVED",null));
        when(gateway.capture("PROVIDER123",first.id())).thenThrow(
                new PayPalGateway.Rejected("MAX_NUMBER_OF_PAYMENT_ATTEMPTS_EXCEEDED","debug123"));
        assertEquals("EXHAUSTED",service.sync(first.id(),true).state());
        assertEquals("EXHAUSTED",service.sync(first.id(),true).state());
        assertEquals("ABANDONED",service.abandon(first.id()).state());
        // The whole group is cancelled in the same transaction, the PayPal payment fails, and it is audited.
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,order.getId()));
        verify(gateway,times(1)).capture("PROVIDER123",first.id());
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,order.getId()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=? AND event_type='BUYER_ABANDONED' AND actor_user_id=?",Integer.class,first.id(),user.getId()));
        // Abandoned is final: a later check or capture never asks PayPal to capture again.
        assertEquals("ABANDONED",service.sync(first.id(),true).state());
        verify(gateway,times(1)).capture("PROVIDER123",first.id());
    }
    @Test void complianceBlocksFurtherCaptureEvenWhenFollowupGetFails()throws Exception{
        var first=service.create(key,request());
        when(gateway.get("PROVIDER123")).thenReturn(evidence("APPROVED",null))
                .thenThrow(new IllegalStateException("timeout")).thenReturn(evidence("APPROVED",null));
        when(gateway.capture("PROVIDER123",first.id())).thenThrow(
                new PayPalGateway.Rejected("COMPLIANCE_VIOLATION","debug123"));
        assertEquals("BLOCKED",service.sync(first.id(),true).state());
        assertEquals("BLOCKED",service.sync(first.id(),true).state());
        verify(gateway,times(1)).capture("PROVIDER123",first.id());
    }
    @Test void completedEvidenceWinsOverExhaustedRefusal()throws Exception{
        var first=service.create(key,request());
        when(gateway.get("PROVIDER123")).thenReturn(evidence("APPROVED",null),evidence("COMPLETED","COMPLETED"));
        when(gateway.capture("PROVIDER123",first.id())).thenThrow(
                new PayPalGateway.Rejected("MAX_NUMBER_OF_PAYMENT_ATTEMPTS_EXCEEDED","debug123"));
        assertEquals("PAID",service.sync(first.id(),true).state());
        assertEquals("SUCCESS",jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,order.getId()));
    }
    @Test void pendingCaptureCannotBeAbandonedAfterExhaustion()throws Exception{
        var first=service.create(key,request());
        when(gateway.get("PROVIDER123")).thenReturn(evidence("APPROVED",null));
        when(gateway.capture("PROVIDER123",first.id())).thenThrow(
                new PayPalGateway.Rejected("MAX_NUMBER_OF_PAYMENT_ATTEMPTS_EXCEEDED","debug123"));
        assertEquals("EXHAUSTED",service.sync(first.id(),true).state());
        when(gateway.get("PROVIDER123")).thenReturn(evidence("APPROVED","PENDING"));
        assertThrows(IllegalArgumentException.class,()->service.abandon(first.id()));
    }
    @Test void createThatSucceedsRemotelyButTimesOutLocallyIsNeverCancelledOnTheMissingId()throws Exception{
        // PayPal creates PROVIDER123 for this request ID, but the response never reaches us.
        List<String> requestIds=new ArrayList<>();
        when(gateway.create(anyString(),any())).thenAnswer(inv->{attempt=inv.getArgument(0);requestIds.add(attempt);throw new IllegalStateException("PayPal create timed out");})
            .thenAnswer(inv->{requestIds.add(inv.getArgument(0));return json.readTree("{\"id\":\"PROVIDER123\",\"links\":[{\"rel\":\"approve\",\"href\":\"https://www.sandbox.paypal.com/checkoutnow?token=PROVIDER123\"}]}");});
        assertThrows(IllegalStateException.class,()->service.create(key,request()));
        String id=jdbc.queryForObject("SELECT id FROM paypal_checkouts WHERE user_id=? AND request_key=?",String.class,user.getId(),key);
        Map<String,Object> row=jdbc.queryForMap("SELECT paypal_order_id,state,request_key,paypal_create_attempted_at FROM paypal_checkouts WHERE id=?",id);
        assertNull(row.get("paypal_order_id"));assertEquals("CREATING",row.get("state"));assertEquals(key,row.get("request_key"));
        assertNotNull(row.get("paypal_create_attempted_at"),"the attempt is recorded before PayPal is called");

        // The buyer cannot cancel on the strength of the missing ID, either directly or by request key.
        assertThrows(IllegalArgumentException.class,()->service.abandon(id));
        assertThrows(IllegalArgumentException.class,()->service.cancelRequest(key));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,order.getId()));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?",String.class,order.getId()));
        assertEquals("CREATING",jdbc.queryForObject("SELECT state FROM paypal_checkouts WHERE id=?",String.class,id));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM paypal_cancelled_requests WHERE user_id=? AND request_key=?",Long.class,user.getId(),key));

        // Checking the status repeats the same idempotent PayPal request and records the real order.
        assertEquals("APPROVAL",service.sync(id,false).state());
        assertEquals(List.of(id,id),requestIds,"the retry reuses the original PayPal request ID");
        assertEquals("PROVIDER123",jdbc.queryForObject("SELECT paypal_order_id FROM paypal_checkouts WHERE id=?",String.class,id));
        assertEquals(key,jdbc.queryForObject("SELECT request_key FROM paypal_checkouts WHERE id=?",String.class,id));
        // With verified provider evidence (CREATED, nothing captured) cancellation is allowed.
        assertEquals("ABANDONED",service.abandon(id).state());
    }
    @Test void checkoutThatNeverSentACreateRequestCanStillBeCancelled()throws Exception{
        // Inserted but never synced (for example the server stopped right after the insert committed):
        // no attempt marker, so no PayPal request was ever sent and nothing can exist at PayPal.
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO paypal_checkouts(id,user_id,request_key,request_hash,checkout_group_id,amount) VALUES(?,?,?,?,?,?)",id,user.getId(),key,"0".repeat(64),group.getId(),new BigDecimal("12.30"));
        assertNull(jdbc.queryForObject("SELECT paypal_create_attempted_at FROM paypal_checkouts WHERE id=?",java.sql.Timestamp.class,id));
        assertEquals("ABANDONED",service.abandon(id).state());
        verify(gateway,never()).create(anyString(),any());
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,order.getId()));
    }

    // ---- Sandbox and Live ----

    private static final String LIVE_LINK="{\"id\":\"PROVIDER123\",\"links\":[{\"rel\":\"approve\",\"href\":\"https://www.paypal.com/checkoutnow?token=PROVIDER123\"}]}";
    private String environmentOf(String id){return jdbc.queryForObject("SELECT environment FROM paypal_checkouts WHERE id=?",String.class,id);}

    @Test void sandboxCheckoutIsRecordedAndReportedAsSandbox()throws Exception{
        var view=service.create(key,request());
        assertEquals("sandbox",environmentOf(view.id()));
        assertTrue(view.sandbox());assertEquals("sandbox",view.environment());
        assertTrue(view.approvalUrl().startsWith("https://www.sandbox.paypal.com/"));
    }

    @Test void liveCheckoutIsRecordedReportedAndCapturedInLive()throws Exception{
        when(gateway.environment()).thenReturn("live");
        when(gateway.create(anyString(),any())).thenAnswer(inv->{attempt=inv.getArgument(0);return json.readTree(LIVE_LINK);});
        var view=service.create(key,request());
        assertEquals("live",environmentOf(view.id()));
        assertFalse(view.sandbox());assertEquals("live",view.environment());
        assertEquals("https://www.paypal.com/checkoutnow?token=PROVIDER123",view.approvalUrl());
        when(gateway.get("PROVIDER123")).thenReturn(evidence("APPROVED",null),evidence("COMPLETED","COMPLETED"));
        var paid=service.sync(view.id(),true);
        assertEquals("PAID",paid.state());assertEquals("live",paid.environment());
        verify(gateway,times(1)).capture("PROVIDER123",view.id());
    }

    @Test void liveServerNeverAcceptsASandboxApprovalLink()throws Exception{
        when(gateway.environment()).thenReturn("live");   // default stub answers with a Sandbox approval link
        assertThrows(IllegalStateException.class,()->service.create(key,request()));
        String id=jdbc.queryForObject("SELECT id FROM paypal_checkouts WHERE user_id=? AND request_key=?",String.class,user.getId(),key);
        assertNull(jdbc.queryForObject("SELECT paypal_order_id FROM paypal_checkouts WHERE id=?",String.class,id));
        assertEquals("CREATING",jdbc.queryForObject("SELECT state FROM paypal_checkouts WHERE id=?",String.class,id));
    }

    @Test void sandboxCheckoutIsNeverSentToLive()throws Exception{
        var sandbox=service.create(key,request());          // APPROVAL in Sandbox, order PROVIDER123
        clearInvocations(gateway);
        when(gateway.environment()).thenReturn("live");
        when(gateway.verifyWebhook(anyMap(),any())).thenReturn(true);

        // Check, capture, cancel and repeat of the original request: refused before any PayPal call.
        assertThrows(IllegalStateException.class,()->service.sync(sandbox.id(),false));
        assertThrows(IllegalStateException.class,()->service.sync(sandbox.id(),true));
        assertThrows(IllegalStateException.class,()->service.abandon(sandbox.id()));
        assertThrows(IllegalStateException.class,()->service.create(key,request()));
        // A Live webhook with the same order ID matches no Live checkout.
        var event=json.createObjectNode().put("id","WH-LIVE-1").put("event_type","CHECKOUT.ORDER.APPROVED");
        event.putObject("resource").put("id","PROVIDER123");
        service.webhook(Map.of(),event);

        verify(gateway,never()).get(anyString());verify(gateway,never()).capture(anyString(),anyString());
        verify(gateway,never()).create(anyString(),any());
        assertEquals("APPROVAL",jdbc.queryForObject("SELECT state FROM paypal_checkouts WHERE id=?",String.class,sandbox.id()));
        assertEquals("sandbox",environmentOf(sandbox.id()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM paypal_webhook_events WHERE checkout_id=?",Integer.class,sandbox.id()));
        // The buyer's open Sandbox checkout is not offered for resuming on the Live server.
        assertNull(service.latest());
        // Back on Sandbox it is the buyer's open checkout again.
        when(gateway.environment()).thenReturn("sandbox");
        assertEquals(sandbox.id(),service.latest().id());
        assertEquals(sandbox.id(),service.forOrder(order.getId()).id());
    }

    @Test void liveCheckoutIsNeverSentToSandbox()throws Exception{
        when(gateway.environment()).thenReturn("live");
        when(gateway.create(anyString(),any())).thenAnswer(inv->{attempt=inv.getArgument(0);return json.readTree(LIVE_LINK);});
        var live=service.create(key,request());
        clearInvocations(gateway);
        when(gateway.environment()).thenReturn("sandbox");
        assertThrows(IllegalStateException.class,()->service.sync(live.id(),true));
        assertThrows(IllegalStateException.class,()->service.abandon(live.id()));
        verify(gateway,never()).get(anyString());verify(gateway,never()).capture(anyString(),anyString());
        assertEquals("live",service.forOrder(order.getId()).environment());
    }

    @Test void unmarkedCheckoutOfTheOtherEnvironmentIsNotMarkedAndCanStillBeCancelled()throws Exception{
        // A Sandbox checkout that never sent a create request, seen by a Live server.
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO paypal_checkouts(id,user_id,request_key,request_hash,checkout_group_id,amount) VALUES(?,?,?,?,?,?)",id,user.getId(),key,"0".repeat(64),group.getId(),new BigDecimal("12.30"));
        when(gateway.environment()).thenReturn("live");
        assertThrows(IllegalStateException.class,()->service.sync(id,false));
        assertNull(jdbc.queryForObject("SELECT paypal_create_attempted_at FROM paypal_checkouts WHERE id=?",java.sql.Timestamp.class,id),
                "no request can be sent, so the checkout keeps its proof that PayPal holds nothing");
        // Nothing ever reached PayPal, so the buyer can release the orders without asking any PayPal.
        assertEquals("ABANDONED",service.abandon(id).state());
        verify(gateway,never()).create(anyString(),any());verify(gateway,never()).get(anyString());
    }

    @Test void unconfiguredServerCreatesNothing(){
        when(gateway.environment()).thenReturn(null);
        assertThrows(IllegalStateException.class,()->service.create(key,request()));
        verify(checkout,never()).checkout(any());verify(gateway,never()).create(anyString(),any());
    }
}
