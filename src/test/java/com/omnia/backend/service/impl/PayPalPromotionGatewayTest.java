package com.omnia.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayPalPromotionGatewayTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void liveApprovalAcceptsOnlyTheLivePayPalHost() {
        var order = json.createObjectNode();
        var links = order.putArray("links");
        links.addObject().put("rel", "approve").put("href", "https://www.sandbox.paypal.com/checkoutnow");
        links.addObject().put("rel", "approve").put("href", "https://www.paypal.com.attacker.example/checkoutnow");
        links.addObject().put("rel", "approve").put("href", "https://user@www.paypal.com/checkoutnow");
        assertThrows(IllegalStateException.class, () -> PayPalPromotionGateway.approvalUrl(order, true));

        links.addObject().put("rel", "payer-action").put("href", "https://www.paypal.com/checkoutnow?token=TEST");
        assertEquals(URI.create("https://www.paypal.com/checkoutnow?token=TEST"),
                PayPalPromotionGateway.approvalUrl(order, true));
    }

    @Test
    void disabledIntegrationCannotOpenAnOrderEvenWithLiveModeSelected() {
        var gateway = new PayPalPromotionGateway(json, false, true, "unused.json");
        var error = assertThrows(IllegalStateException.class,
                () -> gateway.create(UUID.randomUUID(), new BigDecimal("1.00")));
        assertEquals("PayPal promotions are disabled", error.getMessage());
    }
}
