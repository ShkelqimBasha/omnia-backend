package com.omnia.backend.service.impl;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminActionRulesTest {

    @Test
    void logsOnlyChangesInAdminAreas() {
        assertTrue(AdminActionRules.audited("PATCH", "/api/organizations/{organizationId}/status"));
        assertTrue(AdminActionRules.audited("POST", "/api/admin/verifications/{id}/approve"));
        assertTrue(AdminActionRules.audited("DELETE", "/api/categories/{id}"));
        assertTrue(AdminActionRules.audited("PUT", "/api/products/{id}"));
        assertTrue(AdminActionRules.audited("DELETE", "/api/files/{fileId}"));

        assertFalse(AdminActionRules.audited("GET", "/api/admin/users"));
        assertFalse(AdminActionRules.audited("POST", "/api/auth/login"));
        assertFalse(AdminActionRules.audited("PUT", "/api/users/me/password"));
        assertFalse(AdminActionRules.audited("POST", "/api/cart/items"));
        assertFalse(AdminActionRules.audited("POST", "/api/files/upload"));
        assertFalse(AdminActionRules.audited("POST", "/api/products/{productId}/reports"));
        assertFalse(AdminActionRules.audited("POST", "/api/products/{productId}/inquiries"));
        assertFalse(AdminActionRules.audited(null, "/api/admin/users"));
    }

    @Test
    void groupsRoutesByWhatTheyChange() {
        assertEquals("COMPANY", AdminActionRules.targetType("/api/organizations/{organizationId}/status"));
        assertEquals("VERIFICATION", AdminActionRules.targetType("/api/admin/verifications/{id}/reject"));
        assertEquals("VERIFICATION",
                AdminActionRules.targetType("/api/admin/organizations/{organizationId}/verification/revoke"));
        assertEquals("MODERATION", AdminActionRules.targetType("/api/admin/product-reports/{id}/hide"));
        assertEquals("PRODUCT", AdminActionRules.targetType("/api/product-images/{imageId}"));
        assertEquals("CATEGORY", AdminActionRules.targetType("/api/categories/{id}"));
        assertEquals("USER", AdminActionRules.targetType("/api/admin/users/{userId}/role"));
        assertEquals("ORDER", AdminActionRules.targetType("/api/admin/orders/{id}/status"));
        assertEquals("PAYMENT", AdminActionRules.targetType("/api/admin/paypal/recovery/checkouts/{id}/resolve"));
        assertEquals("OTHER", AdminActionRules.targetType("/api/something"));
    }

    @Test
    void labelsInAlbanianWithAGenericFallback() {
        assertEquals("Ndryshoi statusin e kompanisë",
                AdminActionRules.label("PATCH", "/api/organizations/{organizationId}/status"));
        assertEquals("Miratoi verifikimin e shitësit",
                AdminActionRules.label("post", "/api/admin/verifications/{id}/approve"));
        assertEquals("Ndryshim · kompani",
                AdminActionRules.label("PUT", "/api/organizations/{organizationId}/members/{memberId}"));
        assertEquals("Fshirje · produkt", AdminActionRules.label("DELETE", "/api/product-images/{imageId}"));
    }

    @Test
    void targetIsTheFirstPathVariable() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("organizationId", "12");
        variables.put("memberId", "4");
        assertEquals("12", AdminActionRules.targetId(variables));
        assertNull(AdminActionRules.targetId(Map.of()));
        assertEquals(64, AdminActionRules.targetId(Map.of("id", "x".repeat(80))).length());
    }

    @Test
    void neverKeepsAnythingThatLooksLikeASecret() {
        assertEquals("[detaji u fsheh]", AdminActionRules.sanitize("password=hunter22"));
        assertEquals("[detaji u fsheh]", AdminActionRules.sanitize("Fjalëkalimi i ri: abc"));
        assertEquals("[detaji u fsheh]", AdminActionRules.sanitize("Bearer eyJhbGciOi"));
        assertEquals("[detaji u fsheh]", AdminActionRules.sanitize("refresh_token abc"));
        assertEquals("Kompania: Butiku · NIPT: K12345678A",
                AdminActionRules.sanitize("Kompania: Butiku\n· NIPT: K12345678A"));
        assertNull(AdminActionRules.sanitize("   "));
        assertEquals(AdminActionRules.MAX_DETAIL, AdminActionRules.sanitize("a".repeat(900)).length());
    }
}
