package com.omnia.backend.controller;

import com.omnia.backend.dto.response.OrganizationStatisticsResponse;
import com.omnia.backend.service.impl.StatisticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/statistics")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminStatisticsController {

    private final StatisticsService statisticsService;

    public AdminStatisticsController(
            StatisticsService statisticsService
    ) {
        this.statisticsService = statisticsService;
    }

    @GetMapping
    public ResponseEntity<
            OrganizationStatisticsResponse
            >
    getStatistics(
            @RequestParam(defaultValue = "7")
            int days
    ) {
        return ResponseEntity.ok(
                statisticsService
                        .getPlatformStatistics(days)
        );
    }
}