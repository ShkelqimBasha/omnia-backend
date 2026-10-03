package com.omnia.backend.service.impl;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DeliveryPricingTest {
 @Test void centsAreDistributedExactlyOnce() {
  assertEquals(List.of(new BigDecimal("1.67"),new BigDecimal("1.67"),new BigDecimal("1.66")),DeliveryPricing.allocate(new BigDecimal("5.00"),3));
 }
 @Test void manyCompaniesDoNotMultiplyTheFee() {
  for(int companies=1;companies<=501;companies++) {
   assertEquals(new BigDecimal("5.00"),DeliveryPricing.allocate(new BigDecimal("5.00"),companies).stream().reduce(BigDecimal.ZERO,BigDecimal::add));
  }
 }
 @Test void invalidConfigurationsAreRejected() {
  for(BigDecimal value:List.of(new BigDecimal("0"),new BigDecimal("-1"),new BigDecimal("5.001")))
   assertThrows(IllegalArgumentException.class,()->DeliveryPricing.validateSurcharge(value));
  assertThrows(IllegalArgumentException.class,()->DeliveryPricing.allocate(new BigDecimal("5"),0));
 }
}
