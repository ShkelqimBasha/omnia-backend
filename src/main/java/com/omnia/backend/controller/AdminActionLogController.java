package com.omnia.backend.controller;

import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.service.impl.AdminActionLogService;
import com.omnia.backend.service.impl.AdminActionRules;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The admin action log, for the super admin's web panel. */
@RestController
@RequestMapping("/api/admin/action-log")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminActionLogController {

    private final AdminActionLogService service;

    public AdminActionLogController(AdminActionLogService service) {
        this.service = service;
    }

    public record Filters(List<AdminActionLogService.Actor> actors, List<Map<String, String>> targetTypes) {
    }

    @GetMapping
    public PagedResponse<AdminActionLogService.Entry> search(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return service.search(actorId, targetType, q, from, to, page, size);
    }

    @GetMapping("/filters")
    public Filters filters() {
        return new Filters(service.actors(), AdminActionRules.TARGET_TYPES.stream()
                .map(type -> Map.of("value", type, "label", AdminActionRules.typeLabel(type)))
                .toList());
    }
}
