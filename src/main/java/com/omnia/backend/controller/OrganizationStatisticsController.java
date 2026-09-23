package com.omnia.backend.controller;

import com.omnia.backend.dto.response.OrganizationStatisticsResponse;
import com.omnia.backend.service.impl.StatisticsService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/organizations/{organizationId}/statistics"
)
@Validated
public class OrganizationStatisticsController {

    private final StatisticsService statisticsService;

    public OrganizationStatisticsController(
            StatisticsService statisticsService
    ) {
        this.statisticsService = statisticsService;
    }

    @GetMapping
    public ResponseEntity<
            OrganizationStatisticsResponse
            >
    getStatistics(
            @PathVariable
            @Positive
            Long organizationId
    ) {
        return ResponseEntity.ok(
                statisticsService
                        .getOrganizationStatistics(
                                organizationId
                        )
        );
    }
}