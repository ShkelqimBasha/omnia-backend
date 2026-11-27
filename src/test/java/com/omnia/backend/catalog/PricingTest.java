package com.omnia.backend.catalog;

import com.omnia.backend.entity.Product;
import com.omnia.backend.enums.ProductStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingTest {

    private static BigDecimal bd(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    @Test
    void buyerPaysARealDiscountOnly() {
        assertThat(Pricing.buyerPrice(bd("100"), bd("80"))).isEqualByComparingTo("80");
        assertThat(Pricing.buyerPrice(bd("100"), null)).isEqualByComparingTo("100");
        // A negative, zero or too-high discount never changes the charge.
        assertThat(Pricing.buyerPrice(bd("100"), bd("-50"))).isEqualByComparingTo("100");
        assertThat(Pricing.buyerPrice(bd("100"), bd("0"))).isEqualByComparingTo("100");
        assertThat(Pricing.buyerPrice(bd("100"), bd("100"))).isEqualByComparingTo("100");
        assertThat(Pricing.buyerPrice(bd("100"), bd("150"))).isEqualByComparingTo("100");
    }

    @Test
    void storedDiscountIsRejectedWhenNegativeAndDroppedWhenNotLower() {
        assertThatThrownBy(() -> Pricing.normalizeDiscount(bd("100"), bd("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Pricing.normalizeDiscount(bd("100"), bd("0"))).isNull();
        assertThat(Pricing.normalizeDiscount(bd("100"), bd("100"))).isNull();
        assertThat(Pricing.normalizeDiscount(bd("100"), null)).isNull();
        assertThat(Pricing.normalizeDiscount(bd("100"), bd("99.99"))).isEqualByComparingTo("99.99");
    }

    @Test
    void hiddenProductsCannotBeBought() {
        Product hidden = Product.builder().name("X").price(bd("10")).status(ProductStatus.INACTIVE).build();
        assertThatThrownBy(() -> PurchaseGuard.requireListed(hidden))
                .isInstanceOf(IllegalArgumentException.class);
        Product listed = Product.builder().name("Y").price(bd("10")).status(ProductStatus.ACTIVE).build();
        PurchaseGuard.requireListed(listed);
    }
}
