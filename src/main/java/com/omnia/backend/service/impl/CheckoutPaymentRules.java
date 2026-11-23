package com.omnia.backend.service.impl;

import com.omnia.backend.entity.Organization;
import com.omnia.backend.enums.PaymentMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Which payment methods a checkout may use: only the methods that every company in it accepts.
 * The COD minimum/maximum of a company apply to the value of that company's products in the
 * checkout (before coupon and delivery). Messages are shown to buyers, so they are in Albanian.
 */
public final class CheckoutPaymentRules {

    private CheckoutPaymentRules() {
    }

    /** One company's part of a checkout. */
    public record Part(Organization organization, BigDecimal subtotal) {
    }

    public record CompanyResult(Organization organization, BigDecimal subtotal,
                                String cashOnDeliveryReason, String paypalReason) {
        public boolean cashOnDeliveryAvailable() {
            return cashOnDeliveryReason == null;
        }

        public boolean paypalAvailable() {
            return paypalReason == null;
        }
    }

    public record Result(List<CompanyResult> companies) {
        public String reason(PaymentMethod method) {
            for (CompanyResult company : companies) {
                String reason = switch (method) {
                    case CASH_ON_DELIVERY -> company.cashOnDeliveryReason();
                    case PAYPAL -> company.paypalReason();
                    default -> "Kjo mënyrë pagese nuk ofrohet.";
                };
                if (reason != null) return reason;
            }
            return null;
        }

        public boolean available(PaymentMethod method) {
            return reason(method) == null;
        }

        /**
         * Several companies, and a method the cart cannot use although at least one of them
         * accepts it in its settings.
         */
        public boolean mixedRules() {
            if (companies.size() < 2) return false;
            boolean codDiffers = companies.stream().anyMatch(c -> c.organization().isAcceptsCashOnDelivery())
                    && !available(PaymentMethod.CASH_ON_DELIVERY);
            boolean paypalDiffers = companies.stream().anyMatch(c -> c.organization().isAcceptsPaypal())
                    && !available(PaymentMethod.PAYPAL);
            return codDiffers || paypalDiffers;
        }

        /** Explanation for the buyer, or null when nothing needs explaining. */
        public String message() {
            boolean cod = available(PaymentMethod.CASH_ON_DELIVERY);
            boolean paypal = available(PaymentMethod.PAYPAL);
            if (!cod && !paypal) {
                return companies.size() > 1
                        ? "Shporta ka produkte nga biznese me rregulla të ndryshme pagese dhe asnjë mënyrë "
                        + "pagese nuk pranohet nga të gjithë. Bëj porosi të veçanta për secilin biznes."
                        : "Asnjë mënyrë pagese nuk është e disponueshme për këtë porosi.";
            }
            if (mixedRules()) {
                return "Shporta ka produkte nga disa biznese me rregulla të ndryshme pagese: "
                        + "ofrohen vetëm mënyrat që i pranojnë të gjithë.";
            }
            return null;
        }
    }

    public static Result evaluate(List<Part> parts) {
        List<CompanyResult> results = new ArrayList<>();
        for (Part part : parts) {
            Organization org = part.organization();
            BigDecimal subtotal = part.subtotal().setScale(2, RoundingMode.HALF_UP);
            String name = "«" + org.getName() + "»";
            String cod = null;
            if (!org.isAcceptsCashOnDelivery()) {
                cod = "Biznesi " + name + " nuk pranon pagesë në dorëzim.";
            } else if (org.getCodMinOrderAmount() != null && subtotal.compareTo(org.getCodMinOrderAmount()) < 0) {
                cod = "Për pagesë në dorëzim, produktet nga " + name + " duhet të jenë të paktën "
                        + euros(org.getCodMinOrderAmount()) + ".";
            } else if (org.getCodMaxOrderAmount() != null && subtotal.compareTo(org.getCodMaxOrderAmount()) > 0) {
                cod = "Pagesa në dorëzim për produktet nga " + name + " pranohet deri në "
                        + euros(org.getCodMaxOrderAmount()) + ".";
            }
            String paypal = org.isAcceptsPaypal() ? null : "Biznesi " + name + " nuk pranon pagesë me PayPal.";
            results.add(new CompanyResult(org, subtotal, cod, paypal));
        }
        return new Result(results);
    }

    /** Throws with the Albanian reason when the checkout may not use this method. */
    public static void require(PaymentMethod method, List<Part> parts) {
        String reason = evaluate(parts).reason(method);
        if (reason != null) throw new IllegalArgumentException(reason);
    }

    static String euros(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " €";
    }
}
