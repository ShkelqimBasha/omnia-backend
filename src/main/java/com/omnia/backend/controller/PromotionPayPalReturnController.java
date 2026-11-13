package com.omnia.backend.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PromotionPayPalReturnController {

    @GetMapping(value = "/api/promotions/paypal/return", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(
            @RequestParam(name = "cancelled", defaultValue = "false") String cancelled) {
        String message = "true".equalsIgnoreCase(cancelled)
                ? "Mbylle k&#235;t&#235; sked&#235; dhe kthehu n&#235; Omnia."
                : "Mbylle k&#235;t&#235; sked&#235;. N&#235;se e miratove pages&#235;n n&#235; PayPal, "
                    + "kthehu te i nj&#235;jti produkt dhe shtyp Konfirmo pages&#235;n.";

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body("""
                        <!doctype html>
                        <html lang="sq">
                        <head>
                          <meta charset="utf-8">
                          <meta name="viewport" content="width=device-width, initial-scale=1">
                          <title>Kthehu n&#235; Omnia</title>
                        </head>
                        <body>
                          <main>
                            <h1>Kthehu n&#235; Omnia</h1>
                            <p>%s</p>
                            <p><a href="omnia://promotion-return%s">Hap Omnia</a></p>
                          </main>
                        </body>
                        </html>
                        """.formatted(message, "true".equalsIgnoreCase(cancelled) ? "?cancelled=true" : ""));
    }
}
