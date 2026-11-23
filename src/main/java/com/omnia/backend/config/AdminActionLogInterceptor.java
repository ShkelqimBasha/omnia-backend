package com.omnia.backend.config;

import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.impl.AdminActionLogService;
import com.omnia.backend.service.impl.AdminActionRules;
import com.omnia.backend.service.impl.AdminAudit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * After every successful change a platform admin makes (POST/PUT/PATCH/DELETE on an admin area),
 * writes one admin_action_log row. Only the route pattern, path ids, a few enum parameters
 * (status, role) and notes the code adds with {@link AdminAudit#note} are stored — never the body.
 */
@Component
public class AdminActionLogInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AdminActionLogInterceptor.class);

    private final AdminActionLogService logService;
    private final CurrentUserService currentUserService;

    public AdminActionLogInterceptor(AdminActionLogService logService, CurrentUserService currentUserService) {
        this.logService = logService;
        this.currentUserService = currentUserService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        try {
            int status = response.getStatus();
            if (exception != null || status >= 400) {
                return;
            }
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (pattern == null) {
                return;
            }
            String route = pattern.toString();
            if (!AdminActionRules.audited(request.getMethod(), route)
                    || !currentUserService.hasCurrentPlatformAdminAuthority()) {
                return;
            }
            logService.record(request.getMethod(), route, pathVariables(request),
                    detail(request), status);
        } catch (RuntimeException failure) {
            // The admin's change already happened; a log failure must not turn it into an error.
            LOG.warn("Could not write the admin action log: {}", failure.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> pathVariables(HttpServletRequest request) {
        Object value = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return value instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }

    private static String detail(HttpServletRequest request) {
        List<String> parts = new ArrayList<>();
        for (String name : AdminActionRules.SAFE_PARAMETERS) {
            String value = request.getParameter(name);
            if (value != null && value.matches("[A-Za-z_]{1,40}")) {
                parts.add(name + "=" + value);
            }
        }
        Object note = request.getAttribute(AdminAudit.ATTRIBUTE);
        if (note != null) {
            parts.add(note.toString());
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }
}
