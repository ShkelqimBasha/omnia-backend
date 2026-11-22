package com.omnia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.service.impl.PayPalCheckoutService;
import com.omnia.backend.service.impl.PayPalGateway;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController
@RequestMapping(value="/api/paypal",produces=MediaType.APPLICATION_JSON_VALUE)
public class PayPalCheckoutController {
    private final PayPalCheckoutService service;private final PayPalGateway gateway;
    public PayPalCheckoutController(PayPalCheckoutService service,PayPalGateway gateway){this.service=service;this.gateway=gateway;}
    /**
     * Contract with the app: {@code environment} is "live" only when Live is configured and its
     * credentials are in place ({@code enabled} true, {@code sandbox} false). Otherwise the server
     * reports Sandbox, enabled or not, so no app ever takes a misconfigured server for Live.
     */
    @GetMapping("/config") public Map<String,Object> config(){
        String environment=gateway.environment();
        boolean live=PayPalGateway.LIVE.equals(environment);
        return Map.of("enabled",environment!=null,"sandbox",!live,"environment",live?PayPalGateway.LIVE:PayPalGateway.SANDBOX,
                "currency","EUR","cardsEnabled",false);
    }
    /** {@code client=web}: the buyer pays from the web app and PayPal should return them there. */
    @PostMapping("/checkouts/{key}") public PayPalCheckoutService.View create(@PathVariable String key,@RequestParam(name="client",required=false) String client,@Valid @RequestBody CreateOrderRequest request){return service.create(key,request,"web".equals(client));}
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
    @GetMapping(value="/return",produces=MediaType.TEXT_HTML_VALUE) public ResponseEntity<String> browserReturn(@RequestParam(name="checkout", required=false) String checkout,
            @RequestParam(name="client", required=false) String client,@RequestParam(name="cancelled", required=false) String cancelled){
        boolean valid=checkout != null && checkout.matches("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
        String web=gateway.webReturnUrl();
        // Web buyers go straight back to the web app, which checks and captures with the server.
        if(valid && web != null && "web".equals(client)) {
            String target=web+"/"+checkout.toLowerCase(Locale.ROOT)+("true".equals(cancelled)?"?cancelled=true":"");
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
        }
        String name=PayPalGateway.LIVE.equals(gateway.environment())?"PayPal":"PayPal Sandbox";
        return ResponseEntity.ok("<!doctype html><html><meta name='viewport' content='width=device-width'><title>Omnia "+name+"</title><body><h2>Omnia — "+name+"</h2><p>Return to Omnia and tap Check payment. / Kthehu në Omnia dhe shtyp Kontrollo pagesën.</p>" + (valid
                ? "<p><a href='omnia://paypal-return?checkout=" + checkout + "'>Hap Omnia</a></p>"
                        + (web != null ? "<p><a href='" + web + "/" + checkout.toLowerCase(Locale.ROOT) + "'>Vazhdo në web</a></p>" : "")
                : "<p>Hap Historikun e porosive në Omnia për të kontrolluar pagesën.</p>") + "</body></html>");}
}
