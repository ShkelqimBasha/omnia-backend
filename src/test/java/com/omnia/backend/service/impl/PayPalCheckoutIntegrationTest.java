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
    @Autowired PayPalOrderGuard guard;
    private PayPalCheckoutService service;private PayPalGateway gateway;private CurrentUserService current;
    private CheckoutService checkout;private User user;private CheckoutGroup group;private Order order;private String key;
    private final ObjectMapper json=new ObjectMapper();private String attempt;
    @BeforeEach void setup()throws Exception{
        gateway=mock(PayPalGateway.class);current=mock(CurrentUserService.class);checkout=mock(CheckoutService.class);
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
        service=new PayPalCheckoutService(jdbc,current,checkout,mock(OrderService.class),gateway,json,em,manager);key=UUID.randomUUID().toString();
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
}
