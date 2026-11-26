package com.omnia.backend.service.impl;

import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.enums.PaymentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * What the seller owes back for {@code refunded} of an order that was paid out with
     * {@code commissionPercent}: the refunded part minus the commission Omnia kept on it.
     */
    public static BigDecimal adjustmentAmount(BigDecimal refunded, BigDecimal commissionPercent) {
        return amounts(refunded, commissionPercent).net();
    }

    /**
     * Splits the refunded total of a PayPal checkout over its orders, in proportion to each order's total
     * (HALF_UP to cents; the last order takes the rounding rest). A full refund gives every order its own
     * total. Each share is capped at the order's total. {@code orderTotals} in order id order.
     */
    public static List<BigDecimal> refundShares(BigDecimal refundedTotal, List<BigDecimal> orderTotals) {
        List<BigDecimal> shares = new ArrayList<>();
        BigDecimal sum = orderTotals.stream().map(t -> t.setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refundedTotal == null || refundedTotal.signum() <= 0 || sum.signum() <= 0) {
            orderTotals.forEach(t -> shares.add(BigDecimal.ZERO.setScale(2)));
            return shares;
        }
        BigDecimal refunded = refundedTotal.min(sum).setScale(2, RoundingMode.HALF_UP);
        BigDecimal given = BigDecimal.ZERO;
        for (int i = 0; i < orderTotals.size(); i++) {
            BigDecimal total = orderTotals.get(i).setScale(2, RoundingMode.HALF_UP);
            BigDecimal share = i == orderTotals.size() - 1
                    ? refunded.subtract(given)
                    : refunded.multiply(total).divide(sum, 2, RoundingMode.HALF_UP);
            share = share.max(BigDecimal.ZERO).min(total);
            given = given.add(share);
            shares.add(share);
        }
        return shares;
    }

    /**
     * How much of each open adjustment (remaining amounts, oldest first) a payout with {@code available}
     * net settles: oldest first, the last one possibly in part. Never more than {@code available}.
     */
    public static List<BigDecimal> deductions(List<BigDecimal> remaining, BigDecimal available) {
        List<BigDecimal> taken = new ArrayList<>();
        BigDecimal left = available == null ? BigDecimal.ZERO : available.max(BigDecimal.ZERO);
        for (BigDecimal open : remaining) {
            BigDecimal take = open.max(BigDecimal.ZERO).min(left).setScale(2, RoundingMode.HALF_UP);
            taken.add(take);
            left = left.subtract(take);
        }
        return taken;
    }

    /** Normalised transfer reference for the duplicate check: trimmed, inner spaces collapsed, lower case. */
    public static String referenceKey(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        return reference.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }
}
