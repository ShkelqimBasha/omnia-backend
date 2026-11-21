package com.omnia.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PayPalEvidenceTest {
    private final ObjectMapper json=new ObjectMapper();
    private ObjectNode order()throws Exception{return (ObjectNode)json.readTree("""
        {"id":"PROVIDER123","intent":"CAPTURE","status":"COMPLETED","purchase_units":[{
         "reference_id":"local","payee":{"merchant_id":"MERCHANT1"},"amount":{"currency_code":"EUR","value":"12.30"},
         "payments":{"captures":[{"id":"CAPTURE123","status":"COMPLETED","final_capture":true,"create_time":"2026-11-02T10:00:00Z","amount":{"currency_code":"EUR","value":"12.30"}}]}}]}
        """);}
    private PayPalEvidence.Result verify(ObjectNode n){return PayPalEvidence.verify(n,"PROVIDER123","local","MERCHANT1",new BigDecimal("12.30"));}
    @Test void completedProviderEvidenceIsRequired()throws Exception{assertEquals("PAID",verify(order()).state());}
    @Test void rejectsWrongMerchantCurrencyTotalAndReference()throws Exception{
        ObjectNode n=order();((ObjectNode)n.at("/purchase_units/0/payee")).put("merchant_id","OTHER");assertThrows(IllegalArgumentException.class,()->verify(n));
        ObjectNode a=order();((ObjectNode)a.at("/purchase_units/0/amount")).put("value","0.01");assertThrows(IllegalArgumentException.class,()->verify(a));
        ObjectNode c=order();((ObjectNode)c.at("/purchase_units/0/amount")).put("currency_code","USD");assertThrows(IllegalArgumentException.class,()->verify(c));
        ObjectNode r=order();((ObjectNode)r.at("/purchase_units/0")).put("reference_id","other");assertThrows(IllegalArgumentException.class,()->verify(r));
    }
    @Test void approvalAndPendingAreNeverPaid()throws Exception{
        ObjectNode n=order();n.put("status","APPROVED");((ObjectNode)n.at("/purchase_units/0")).remove("payments");assertEquals("APPROVED",verify(n).state());
        ObjectNode p=order();((ObjectNode)p.at("/purchase_units/0/payments/captures/0")).put("status","PENDING");assertEquals("PENDING",verify(p).state());
    }
    @Test void partialRefundRequiresReviewAndPartialCaptureCannotSettle()throws Exception{
        ObjectNode n=order();((ObjectNode)n.at("/purchase_units/0/payments/captures/0")).put("status","PARTIALLY_REFUNDED");assertEquals("REVIEW",verify(n).state());
        ObjectNode p=order();((ObjectNode)p.at("/purchase_units/0/payments/captures/0")).put("final_capture",false);assertThrows(IllegalArgumentException.class,()->verify(p));
    }
    @Test void refundedDeclinedAndFailedCapturesNeverSettleAsPaid()throws Exception{
        for(String[] c:new String[][]{{"REFUNDED","REFUNDED"},{"DECLINED","FAILED"},{"FAILED","FAILED"}}){
            ObjectNode n=order();((ObjectNode)n.at("/purchase_units/0/payments/captures/0")).put("status",c[0]);
            PayPalEvidence.Result result=verify(n);
            assertEquals(c[1],result.state(),c[0]);assertEquals("CAPTURE123",result.captureId());assertNull(result.paidAt());
        }
    }
    @Test void orderStatusWithoutCaptureMapsToApprovalFailureOrPending()throws Exception{
        for(String[] c:new String[][]{{"CREATED","APPROVAL"},{"PAYER_ACTION_REQUIRED","APPROVAL"},{"VOIDED","FAILED"},{"SAVED","PENDING"}}){
            ObjectNode n=order();n.put("status",c[0]);((ObjectNode)n.at("/purchase_units/0/payments")).putArray("captures");
            PayPalEvidence.Result result=verify(n);
            assertEquals(c[1],result.state(),c[0]);assertNull(result.captureId());
        }
    }
    @Test void rejectsMalformedProviderShapes()throws Exception{
        ObjectNode none=order();none.remove("purchase_units");assertThrows(IllegalArgumentException.class,()->verify(none));
        ObjectNode two=order();((com.fasterxml.jackson.databind.node.ArrayNode)two.get("purchase_units")).add(two.at("/purchase_units/0").deepCopy());assertThrows(IllegalArgumentException.class,()->verify(two));
        ObjectNode split=order();var captures=(com.fasterxml.jackson.databind.node.ArrayNode)split.at("/purchase_units/0/payments/captures");captures.add(captures.get(0).deepCopy());assertThrows(IllegalArgumentException.class,()->verify(split));
        ObjectNode intent=order();intent.put("intent","AUTHORIZE");assertThrows(IllegalArgumentException.class,()->verify(intent));
        ObjectNode notDone=order();notDone.put("status","APPROVED");assertThrows(IllegalArgumentException.class,()->verify(notDone));
        ObjectNode badId=order();((ObjectNode)badId.at("/purchase_units/0/payments/captures/0")).put("id","bad id");assertThrows(IllegalArgumentException.class,()->verify(badId));
        ObjectNode badTime=order();((ObjectNode)badTime.at("/purchase_units/0/payments/captures/0")).put("create_time","yesterday");assertThrows(IllegalArgumentException.class,()->verify(badTime));
        ObjectNode badValue=order();((ObjectNode)badValue.at("/purchase_units/0/amount")).put("value","twelve");assertThrows(IllegalArgumentException.class,()->verify(badValue));
        ObjectNode captureAmount=order();((ObjectNode)captureAmount.at("/purchase_units/0/payments/captures/0/amount")).put("value","12.29");assertThrows(IllegalArgumentException.class,()->verify(captureAmount));
    }
    @Test void rejectsUntrustedApprovalHosts()throws Exception{
        for(String url:new String[]{"http://www.sandbox.paypal.com/x","https://www.sandbox.paypal.com.evil.test/x","https://www.sandbox.paypal.com@evil.test/x","https://evil.test/x"}){
            var n=json.createObjectNode();n.putArray("links").addObject().put("rel","approve").put("href",url);
            assertThrows(IllegalStateException.class,()->PayPalGateway.approval(n,"sandbox"));
        }
        var n=json.createObjectNode();n.putArray("links").addObject().put("rel","payer-action").put("href","https://www.sandbox.paypal.com/checkoutnow?token=TEST123");
        assertTrue(PayPalGateway.approval(n,"sandbox").startsWith("https://www.sandbox.paypal.com/"));
    }
    @Test void approvalLinkMustBeOnTheCheckoutsOwnEnvironment()throws Exception{
        var live=json.createObjectNode();live.putArray("links").addObject().put("rel","approve").put("href","https://www.paypal.com/checkoutnow?token=LIVE123");
        var sandbox=json.createObjectNode();sandbox.putArray("links").addObject().put("rel","approve").put("href","https://www.sandbox.paypal.com/checkoutnow?token=TEST123");
        assertEquals("https://www.paypal.com/checkoutnow?token=LIVE123",PayPalGateway.approval(live,"live"));
        assertThrows(IllegalStateException.class,()->PayPalGateway.approval(sandbox,"live"),"a Live checkout never shows a Sandbox page");
        assertThrows(IllegalStateException.class,()->PayPalGateway.approval(live,"sandbox"),"a Sandbox checkout never shows a Live page");
        for(String unknown:new String[]{null,"","LIVE","production"})assertThrows(IllegalStateException.class,()->PayPalGateway.approval(live,unknown));
        for(String url:new String[]{"http://www.paypal.com/x","https://www.paypal.com.evil.test/x","https://www.paypal.com@evil.test/x","https://www.paypal.com:8443/x","https://paypal.com/x"}){
            var n=json.createObjectNode();n.putArray("links").addObject().put("rel","approve").put("href",url);
            assertThrows(IllegalStateException.class,()->PayPalGateway.approval(n,"live"),url);
        }
    }
}
