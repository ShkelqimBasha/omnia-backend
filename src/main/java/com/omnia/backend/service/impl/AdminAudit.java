package com.omnia.backend.service.impl;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Lets code add a short, human description to the admin action log entry of the current
 * request ("Kompania: Butiku · Arsyeja: dokument i palexueshëm"). Only plain descriptions:
 * never passwords, tokens or request bodies. Outside a web request it does nothing.
 */
public final class AdminAudit {

    public static final String ATTRIBUTE = AdminAudit.class.getName() + ".detail";

    private AdminAudit() {
    }

    public static void note(String detail) {
        if (detail == null || detail.isBlank()) {
            return;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        Object current = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        String next = current == null ? detail.trim() : current + " · " + detail.trim();
        attributes.setAttribute(ATTRIBUTE, next, RequestAttributes.SCOPE_REQUEST);
    }
}
