package com.omnia.backend.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Which platform-admin requests go into the admin action log, and how they read in Albanian.
 * Works on the route pattern (/api/organizations/{organizationId}/status), never on request
 * bodies, so passwords, tokens and other secrets can never end up in the log.
 */
public final class AdminActionRules {

    static final int MAX_DETAIL = 500;

    /** Only these areas are platform administration; personal actions (cart, profile, login…) are not logged. */
    private static final List<String> AUDITED_PREFIXES = List.of(
            "/api/admin/",
            "/api/organizations",
            "/api/categories",
            "/api/products",
            "/api/product-images/",
            "/api/product-variants/",
            "/api/reviews/admin/",
            "/api/coupons",
            "/api/roles",
            "/api/files/{fileId}"
    );

    /** A platform admin acting as a normal user (reporting, asking about a listing). */
    private static final List<String> PERSONAL_ROUTES = List.of(
            "/api/products/{productId}/reports",
            "/api/products/{productId}/inquiries"
    );

    /** Request parameters that are safe to copy into the detail (enum values only). */
    public static final List<String> SAFE_PARAMETERS = List.of("status", "role");

    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|passwd|fjal[eë]kalim|secret|token|authorization|bearer|api[-_ ]?key)");

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("POST /api/organizations", "Krijoi kompaninë"),
            Map.entry("POST /api/organizations/editor", "Krijoi kompaninë"),
            Map.entry("PUT /api/organizations/{organizationId}", "Ndryshoi kompaninë"),
            Map.entry("PUT /api/organizations/{id}/editor", "Ndryshoi kompaninë"),
            Map.entry("PATCH /api/organizations/{organizationId}/status", "Ndryshoi statusin e kompanisë"),
            Map.entry("PUT /api/organizations/{organizationId}/logo", "Ndryshoi logon e kompanisë"),
            Map.entry("POST /api/organizations/{organizationId}/verification", "Dërgoi kërkesë verifikimi"),
            Map.entry("POST /api/categories", "Krijoi kategorinë"),
            Map.entry("PUT /api/categories/{id}", "Ndryshoi kategorinë"),
            Map.entry("DELETE /api/categories/{id}", "Fshiu kategorinë"),
            Map.entry("POST /api/products", "Krijoi produktin"),
            Map.entry("PUT /api/products/{id}", "Ndryshoi produktin"),
            Map.entry("DELETE /api/products/{id}", "Çaktivizoi produktin"),
            Map.entry("POST /api/admin/users", "Krijoi përdoruesin"),
            Map.entry("PATCH /api/admin/users/{userId}/status", "Ndryshoi statusin e përdoruesit"),
            Map.entry("PATCH /api/admin/users/{userId}/role", "Ndryshoi rolin e përdoruesit"),
            Map.entry("DELETE /api/admin/users/{userId}", "Fshiu përdoruesin"),
            Map.entry("PATCH /api/admin/orders/{id}/status", "Ndryshoi statusin e porosisë"),
            Map.entry("POST /api/admin/paypal/recovery/checkouts/{id}/resolve", "Zgjidhi pagesën PayPal"),
            Map.entry("POST /api/admin/paypal/recovery/cases/{caseId}/close", "Mbylli rastin PayPal"),
            Map.entry("POST /api/admin/verifications/{id}/approve", "Miratoi verifikimin e shitësit"),
            Map.entry("POST /api/admin/verifications/{id}/reject", "Refuzoi verifikimin e shitësit"),
            Map.entry("POST /api/admin/organizations/{organizationId}/verification/revoke", "Hoqi verifikimin e shitësit"),
            Map.entry("POST /api/admin/product-reports/{id}/hide", "Fshehu produktin e raportuar"),
            Map.entry("POST /api/admin/product-reports/{id}/dismiss", "Hodhi poshtë raportimin"),
            Map.entry("POST /api/admin/product-reports/{id}/warn", "Paralajmëroi kompaninë"),
            Map.entry("POST /api/admin/product-reports/{id}/restore", "Rikthei produktin e fshehur"),
            Map.entry("PATCH /api/reviews/admin/{reviewId}/visibility", "Ndryshoi dukshmërinë e vlerësimit"),
            Map.entry("PUT /api/reviews/admin/{reviewId}/reply", "Iu përgjigj vlerësimit"),
            Map.entry("DELETE /api/reviews/admin/{reviewId}/reply", "Fshiu përgjigjen e vlerësimit"),
            Map.entry("DELETE /api/reviews/admin/{reviewId}", "Fshiu vlerësimin"),
            Map.entry("DELETE /api/files/{fileId}", "Fshiu skedarin")
    );

    private static final Map<String, String> TYPE_LABELS = Map.ofEntries(
            Map.entry("COMPANY", "kompani"),
            Map.entry("CATEGORY", "kategori"),
            Map.entry("PRODUCT", "produkt"),
            Map.entry("USER", "përdorues"),
            Map.entry("ORDER", "porosi"),
            Map.entry("PAYMENT", "pagesë"),
            Map.entry("VERIFICATION", "verifikim"),
            Map.entry("MODERATION", "moderim"),
            Map.entry("REVIEW", "vlerësim"),
            Map.entry("COUPON", "kupon"),
            Map.entry("ROLE", "rol"),
            Map.entry("FILE", "skedar"),
            Map.entry("OTHER", "tjetër")
    );

    /** The values the web filter offers, in display order. */
    public static final List<String> TARGET_TYPES = List.of(
            "COMPANY", "VERIFICATION", "MODERATION", "PRODUCT", "CATEGORY",
            "USER", "ORDER", "PAYMENT", "REVIEW", "COUPON", "ROLE", "FILE", "OTHER");

    private AdminActionRules() {
    }

    public static boolean audited(String method, String route) {
        if (method == null || route == null) {
            return false;
        }
        String verb = method.toUpperCase(Locale.ROOT);
        if (!verb.equals("POST") && !verb.equals("PUT") && !verb.equals("PATCH") && !verb.equals("DELETE")) {
            return false;
        }
        if (route.equals("/api/files/upload") || PERSONAL_ROUTES.contains(route)) {
            return false;
        }
        if (route.equals("/api/files/{fileId}")) {
            return verb.equals("DELETE");
        }
        return AUDITED_PREFIXES.stream().anyMatch(route::startsWith);
    }

    public static String targetType(String route) {
        if (route == null) {
            return "OTHER";
        }
        if (route.contains("/verification")) {
            return "VERIFICATION";
        }
        if (route.startsWith("/api/admin/product-reports")) {
            return "MODERATION";
        }
        if (route.startsWith("/api/organizations") || route.startsWith("/api/admin/organizations")) {
            return "COMPANY";
        }
        if (route.startsWith("/api/categories")) {
            return "CATEGORY";
        }
        if (route.startsWith("/api/products") || route.startsWith("/api/product-")) {
            return "PRODUCT";
        }
        if (route.startsWith("/api/admin/users")) {
            return "USER";
        }
        if (route.startsWith("/api/admin/orders")) {
            return "ORDER";
        }
        if (route.startsWith("/api/admin/paypal")) {
            return "PAYMENT";
        }
        if (route.startsWith("/api/reviews")) {
            return "REVIEW";
        }
        if (route.startsWith("/api/coupons")) {
            return "COUPON";
        }
        if (route.startsWith("/api/roles")) {
            return "ROLE";
        }
        if (route.startsWith("/api/files")) {
            return "FILE";
        }
        return "OTHER";
    }

    /** The id the action was about: the first path variable, e.g. {organizationId}. */
    public static String targetId(Map<String, String> pathVariables) {
        if (pathVariables == null || pathVariables.isEmpty()) {
            return null;
        }
        String value = pathVariables.values().iterator().next();
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    public static String label(String method, String route) {
        String key = (method == null ? "" : method.toUpperCase(Locale.ROOT)) + " " + route;
        String label = LABELS.get(key);
        if (label != null) {
            return label;
        }
        String verb = switch (method == null ? "" : method.toUpperCase(Locale.ROOT)) {
            case "POST" -> "Veprim";
            case "DELETE" -> "Fshirje";
            default -> "Ndryshim";
        };
        return verb + " · " + typeLabel(targetType(route));
    }

    public static String typeLabel(String targetType) {
        return TYPE_LABELS.getOrDefault(targetType, "tjetër");
    }

    /**
     * Last line of defence: drops any detail that mentions a secret and trims it to the column.
     * Code only ever writes plain descriptions here (names, statuses, reasons).
     */
    public static String sanitize(String detail) {
        if (detail == null) {
            return null;
        }
        String clean = detail.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) {
            return null;
        }
        if (SECRET.matcher(clean).find()) {
            return "[detaji u fsheh]";
        }
        return clean.length() > MAX_DETAIL ? clean.substring(0, MAX_DETAIL - 1) + "…" : clean;
    }
}
