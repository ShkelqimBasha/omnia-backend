package com.omnia.backend.service.impl;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayPalGatewayCoverage35Test {
    @TempDir Path dir;ObjectMapper json=new ObjectMapper();PayPalGateway gateway;HttpClient http;Path credentials;
    @BeforeEach void setup() throws Exception {
        credentials=dir.resolve("sandbox.json");configure("client-test");gateway=new PayPalGateway(json);http=mock(HttpClient.class);
        ReflectionTestUtils.setField(gateway,"http",http);ReflectionTestUtils.setField(gateway,"enabled",true);
        ReflectionTestUtils.setField(gateway,"file",credentials.toString());ReflectionTestUtils.setField(gateway,"publicBase","https://api.example.com");
    }
    void configure(String client) throws Exception { json.writeValue(credentials.toFile(),Map.of("clientId",client,"clientSecret","fake-test-secret","merchantId","TESTMERCHANT1","webhookId","WH-TEST")); }
    @SuppressWarnings("unchecked") HttpResponse<String> response(int status,String body) {
        HttpResponse<String> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(body);return response;
    }
    @SuppressWarnings("unchecked") void responses(HttpResponse<String>... values) throws Exception {
        var stub=when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)));
        stub.thenReturn(values[0],Arrays.copyOfRange(values,1,values.length));
    }
    HttpResponse<String> tokenResponse(){return response(200,"{\"access_token\":\"test-token\",\"expires_in\":3600}");}
    List<HttpRequest> requests(int count) throws Exception {
        var captor=ArgumentCaptor.forClass(HttpRequest.class);verify(http,times(count)).send(captor.capture(),any(HttpResponse.BodyHandler.class));return captor.getAllValues();
    }
    String body(HttpRequest request) throws Exception {
        var result=new CompletableFuture<String>();var out=new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s){s.request(Long.MAX_VALUE);}
            public void onNext(ByteBuffer b){byte[] bytes=new byte[b.remaining()];b.get(bytes);out.writeBytes(bytes);}
            public void onError(Throwable e){result.completeExceptionally(e);}
            public void onComplete(){result.complete(out.toString(StandardCharsets.UTF_8));}
        });return result.get(2,TimeUnit.SECONDS);
    }
    @Test void createUsesSandboxMerchantCurrencyAndIdempotencyAndTrustedReturnUrl() throws Exception {
        responses(tokenResponse(),response(201,"{\"id\":\"ORDER123\"}"));
        assertEquals("ORDER123",gateway.create("local-reference",new BigDecimal("38.50")).path("id").asText());
        var req=requests(2);assertEquals("api-m.sandbox.paypal.com",req.get(0).uri().getHost());
        assertTrue(req.get(0).headers().firstValue("Authorization").orElseThrow().startsWith("Basic "));
        var create=req.get(1);assertEquals("create-local-reference",create.headers().firstValue("PayPal-Request-Id").orElseThrow());
        assertEquals("Bearer test-token",create.headers().firstValue("Authorization").orElseThrow());
        var payload=json.readTree(body(create));var unit=payload.path("purchase_units").get(0);
        assertEquals("38.50",unit.at("/amount/value").asText());assertEquals("EUR",unit.at("/amount/currency_code").asText());
        assertEquals("TESTMERCHANT1",unit.at("/payee/merchant_id").asText());assertEquals("local-reference",unit.path("custom_id").asText());
        assertEquals("https://api.example.com/api/paypal/return",payload.at("/payment_source/paypal/experience_context/return_url").asText());
    }
    @Test void getAndCaptureReuseTokenAndCaptureRetainsIdempotencyKey() throws Exception {
        responses(tokenResponse(),response(200,"{}"),response(201,"{\"status\":\"COMPLETED\"}"));
        gateway.get("ORDER123");assertEquals("COMPLETED",gateway.capture("ORDER123","checkout-key").path("status").asText());
        var req=requests(3);assertEquals("GET",req.get(1).method());assertTrue(req.get(1).headers().firstValue("PayPal-Request-Id").isEmpty());
        assertEquals("POST",req.get(2).method());assertEquals("capture-checkout-key",req.get(2).headers().firstValue("PayPal-Request-Id").orElseThrow());
        assertEquals("{}",body(req.get(2)));
    }
    @Test void expiredTokenOrRotatedClientIsRefreshed() throws Exception {
        responses(tokenResponse(),tokenResponse(),tokenResponse());gateway.token();
        ReflectionTestUtils.setField(gateway,"tokenExpires",0L);gateway.token();configure("new-client");gateway.token();requests(3);
    }
    @Test void veryShortTokenLifetimeIsNotReused() throws Exception {
        responses(response(200,"{\"access_token\":\"short\",\"expires_in\":1}"),tokenResponse());
        assertEquals("short",gateway.token());assertEquals("test-token",gateway.token());requests(2);
    }
    @Test void missingTokenCannotAuthorizePayments() throws Exception {
        responses(response(200,"{}"));assertThrows(IllegalStateException.class,()->gateway.get("ORDER123"));requests(1);
    }
    @ParameterizedTest @ValueSource(strings={"COMPLIANCE_VIOLATION","MAX_NUMBER_OF_PAYMENT_ATTEMPTS_EXCEEDED"})
    void terminalCaptureRejectionsPreserveSafeDebugReference(String issue) throws Exception {
        responses(tokenResponse(),response(422,json.writeValueAsString(Map.of("debug_id","debug_123","details",List.of(Map.of("issue",issue))))));
        var error=assertThrows(PayPalGateway.Rejected.class,()->gateway.capture("ORDER123","key"));
        assertTrue(error.stopCapture());assertEquals(issue,error.issue);assertEquals("debug_123",error.debugId);requests(2);
    }
    @ParameterizedTest @ValueSource(ints={199,400,422,500})
    void otherHttpFailuresHaveNoProviderBodyOrCredentialLeak(int status) throws Exception {
        responses(response(status,"{\"message\":\"private-provider-detail\",\"details\":[{\"issue\":\"OTHER\"}]}"));
        var error=assertThrows(IllegalStateException.class,()->gateway.token());assertFalse(error.getMessage().contains("private-provider-detail"));
    }
    @ParameterizedTest @ValueSource(strings={"not-json","<html>error</html>"})
    void malformedResponseFailsClosed(String body) throws Exception {
        responses(response(200,body));assertThrows(IllegalStateException.class,()->gateway.token());
    }
    @Test void oversizedResponseIsRejected() throws Exception {
        responses(response(200,"x".repeat(262145)));assertThrows(IllegalStateException.class,()->gateway.token());
    }
    @Test void transportFailureDoesNotLeakInternalMessage() throws Exception {
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("private-secret"));
        var error=assertThrows(IllegalStateException.class,()->gateway.token());assertFalse(error.getMessage().contains("private-secret"));
    }
    @Test void interruptionRestoresThreadFlag() throws Exception {
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException());
        try {assertThrows(IllegalStateException.class,()->gateway.token());assertTrue(Thread.currentThread().isInterrupted());}
        finally {Thread.interrupted();}
    }
    Map<String,String> headers(){var h=new HashMap<String,String>();for(String k:List.of("auth-algo","cert-url","transmission-id","transmission-sig","transmission-time"))h.put("paypal-"+k,"test");return h;}
    @ParameterizedTest @ValueSource(strings={"SUCCESS","FAILURE"})
    void webhookVerificationUsesConfiguredWebhookAndReturnsProviderVerdict(String verdict) throws Exception {
        responses(tokenResponse(),response(200,"{\"verification_status\":\""+verdict+"\"}"));var event=json.readTree("{\"id\":\"WH-EVENT\"}");
        assertEquals(verdict.equals("SUCCESS"),gateway.verifyWebhook(headers(),event));var body=json.readTree(body(requests(2).get(1)));
        assertEquals("WH-TEST",body.path("webhook_id").asText());assertEquals(event,body.path("webhook_event"));assertEquals("test",body.path("transmission_id").asText());
    }
    @Test void incompleteOrUnboundedWebhookHeadersNeverReachNetwork() {
        for(String key:headers().keySet())for(String value:new String[]{null," ","x".repeat(4097)}) {var h=headers();h.put(key,value);assertFalse(gateway.verifyWebhook(h,json.createObjectNode()));}
        verifyNoInteractions(http);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"../bad","lowercase","1234","A1234?token=x"})
    void invalidIdsNeverReachNetwork(String id) {assertThrows(IllegalArgumentException.class,()->gateway.get(id));assertThrows(IllegalArgumentException.class,()->gateway.capture(id,"key"));verifyNoInteractions(http);}
    @Test void configurationMustBeExplicitValidAndSandboxReady() throws Exception {
        assertTrue(gateway.ready());ReflectionTestUtils.setField(gateway,"enabled",false);assertFalse(gateway.ready());
        ReflectionTestUtils.setField(gateway,"enabled",true);
        for(String path:new String[]{null," ",dir.resolve("missing").toString()}){ReflectionTestUtils.setField(gateway,"file",path);assertFalse(gateway.ready());}
        ReflectionTestUtils.setField(gateway,"file",credentials.toString());
        for(String base:new String[]{"http://api.example.com","https://api.example.com/redirect","https://user@api.example.com"}){ReflectionTestUtils.setField(gateway,"publicBase",base);assertFalse(gateway.ready());}
        ReflectionTestUtils.setField(gateway,"publicBase","https://api.example.com");
        for(String field:List.of("clientId","clientSecret","merchantId","webhookId")){configure("client");var c=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(credentials.toFile());c.put(field,"");json.writeValue(credentials.toFile(),c);assertFalse(gateway.ready());}
        Files.writeString(credentials,"not-json");assertFalse(gateway.ready());assertThrows(IllegalStateException.class,gateway::requireReady);verifyNoInteractions(http);
    }
}
