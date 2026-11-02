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
    @Test void rejectsUntrustedApprovalHosts()throws Exception{
        for(String url:new String[]{"http://www.sandbox.paypal.com/x","https://www.sandbox.paypal.com.evil.test/x","https://www.sandbox.paypal.com@evil.test/x","https://evil.test/x"}){
            var n=json.createObjectNode();n.putArray("links").addObject().put("rel","approve").put("href",url);
            assertThrows(IllegalStateException.class,()->PayPalGateway.approval(n));
        }
        var n=json.createObjectNode();n.putArray("links").addObject().put("rel","payer-action").put("href","https://www.sandbox.paypal.com/checkoutnow?token=TEST123");
        assertTrue(PayPalGateway.approval(n).startsWith("https://www.sandbox.paypal.com/"));
    }
}
