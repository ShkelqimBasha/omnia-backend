package com.omnia.backend.service.impl;

import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.enums.PaymentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Which orders Omnia owes to a seller, and the payout amounts.
 *
 * <p>An order is owed ("paid online and delivered") when all of these hold:
 * <ul>
 *   <li>it belongs to a seller (organization) and has no payout yet;</li>
 *   <li>its payment is PAYPAL with status SUCCESS: the capture was verified at PayPal
 *       (PayPalCheckoutService / recovery "accept as paid"). A full refund turns the payment into
 *       REFUNDED and a partial refund back into PENDING (manual review), so neither is owed;</li>
 *   <li>its PayPal checkout is not REFUNDED or REVIEW (partial refund), as a second guard;</li>
 *   <li>the order is DELIVERED: the seller has handed over the goods.</li>
 * </ul>
 * Cash on delivery is collected by the seller and is never owed. CARD and GOOGLE_PAY have no
 * capture flow in Omnia, so they are not treated as money Omnia holds.
 *
 * <p>The SQL in SellerPayoutService mirrors {@link #eligible}.
 */
public final class PayoutRules {

    public static final BigDecimal MAX_COMMISSION_PERCENT = new BigDecimal("50");

    /** PayPal checkout states that mean the money was (partly) given back. */
    public static final Set<String> REFUNDED_CHECKOUT_STATES = Set.of("REFUNDED", "REVIEW");

    private PayoutRules() {
    }

    public static boolean eligible(Long organizationId, Long payoutId, OrderStatus orderStatus,
                                   PaymentMethod method, PaymentStatus paymentStatus,
                                   String paypalCheckoutState) {
        return organizationId != null
                && payoutId == null
                && orderStatus == OrderStatus.DELIVERED
                && method == PaymentMethod.PAYPAL
                && paymentStatus == PaymentStatus.SUCCESS
                && (paypalCheckoutState == null || !REFUNDED_CHECKOUT_STATES.contains(paypalCheckoutState));
    }

    /** The configured percent, checked to be within 0–50 and kept to two decimals. */
    public static BigDecimal commissionPercent(BigDecimal configured) {
        if (configured == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (configured.signum() < 0 || configured.compareTo(MAX_COMMISSION_PERCENT) > 0) {
            throw new IllegalStateException(
                    "app.payouts.commission-percent must be between 0 and 50, was " + configured);
        }
        return configured.setScale(2, RoundingMode.HALF_UP);
    }

    public record Amounts(BigDecimal gross, BigDecimal commission, BigDecimal net) {
    }

    /** Commission on the whole payout, rounded HALF_UP to cents; net = gross − commission. */
    public static Amounts amounts(BigDecimal gross, BigDecimal commissionPercent) {
        BigDecimal g = (gross == null ? BigDecimal.ZERO : gross).setScale(2, RoundingMode.HALF_UP);
        BigDecimal percent = commissionPercent == null ? BigDecimal.ZERO : commissionPercent;
        BigDecimal commission = g.multiply(percent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new Amounts(g, commission, g.subtract(commission));
    }
}
