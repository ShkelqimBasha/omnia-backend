package com.omnia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.service.impl.PayPalCheckoutService;
import com.omnia.backend.service.impl.PayPalGateway;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import java.util.*;

@RestController
@RequestMapping(value="/api/paypal",produces=MediaType.APPLICATION_JSON_VALUE)
public class PayPalCheckoutController {
    private final PayPalCheckoutService service;private final PayPalGateway gateway;
    public PayPalCheckoutController(PayPalCheckoutService service,PayPalGateway gateway){this.service=service;this.gateway=gateway;}
    @GetMapping("/config") public Map<String,Object> config(){return Map.of("enabled",gateway.ready(),"sandbox",true,"currency","EUR","cardsEnabled",false);}
    @PostMapping("/checkouts/{key}") public PayPalCheckoutService.View create(@PathVariable String key,@Valid @RequestBody CreateOrderRequest request){return service.create(key,request);}
    @GetMapping("/pending") public PayPalCheckoutService.View pending(){return service.latest();}
    @GetMapping("/orders/{id}") public PayPalCheckoutService.View order(@PathVariable long id){return service.forOrder(id);}
    @PostMapping("/checkouts/{id}/sync") public PayPalCheckoutService.View sync(@PathVariable String id){return service.sync(id,false);}
    @PostMapping("/checkouts/{id}/capture") public PayPalCheckoutService.View capture(@PathVariable String id){return service.sync(id,true);}
    @PostMapping("/checkouts/{id}/abandon") public PayPalCheckoutService.View abandon(@PathVariable String id){return service.abandon(id);}
    @PostMapping("/requests/{key}/cancel") public Map<String,Boolean> cancelRequest(@PathVariable String key){service.cancelRequest(key);return Map.of("cancelled",true);}
    @PostMapping("/webhook") public Map<String,Boolean> webhook(@RequestHeader Map<String,String> headers,@RequestBody JsonNode event){
        Map<String,String> lower=new HashMap<>();headers.forEach((k,v)->lower.put(k.toLowerCase(Locale.ROOT),v));
        service.webhook(lower,event);return Map.of("received",true);
    }
    // A browser redirect is never proof of payment. The authenticated app checks/captures with the server.
    @GetMapping(value="/return",produces=MediaType.TEXT_HTML_VALUE) public String browserReturn(){return "<!doctype html><html><meta name='viewport' content='width=device-width'><title>Omnia Sandbox</title><body><h2>Omnia — PayPal Sandbox</h2><p>Return to Omnia and tap Check payment. / Kthehu në Omnia dhe shtyp Kontrollo pagesën.</p></body></html>";}
}
