package com.omnia.backend.controller;

import com.omnia.backend.service.impl.DeliveryPricing;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/checkout/delivery-options")
public class DeliveryOptionsController {
    @Value("${omnia.delivery.express-surcharge:5.00}")
    private BigDecimal expressSurcharge = DeliveryPricing.DEFAULT_EXPRESS_SURCHARGE;
    @GetMapping
    public Options getOptions() {
        return new Options(DeliveryPricing.validateSurcharge(expressSurcharge));
    }
    public record Options(BigDecimal expressSurcharge) {}
}
