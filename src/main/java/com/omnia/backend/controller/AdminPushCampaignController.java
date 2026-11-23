package com.omnia.backend.controller;

import com.omnia.backend.service.impl.PushCampaigns;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Segmented push campaigns for platform admins (web panel: /paneli/admin/fushatat). */
@RestController
@RequestMapping("/api/admin/push-campaigns")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class AdminPushCampaignController {

    private final PushCampaigns campaigns;

    public AdminPushCampaignController(PushCampaigns campaigns) {
        this.campaigns = campaigns;
    }

    public record Update(PushCampaigns.Draft campaign, long revision) {}

    public record Schedule(String scheduledAt, long revision) {}

    @GetMapping
    public List<PushCampaigns.Campaign> list() {
        return campaigns.list();
    }

    @GetMapping("/{id}")
    public PushCampaigns.Campaign get(@PathVariable long id) {
        return campaigns.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PushCampaigns.Campaign create(@RequestBody PushCampaigns.Draft body) {
        return campaigns.create(require(body));
    }

    @PutMapping("/{id}")
    public PushCampaigns.Campaign update(@PathVariable long id, @RequestBody Update body) {
        if (body == null) throw new IllegalArgumentException("Campaign required");
        return campaigns.update(id, require(body.campaign()), body.revision());
    }

    /** Audience size for a segment, before saving. */
    @PostMapping("/audience")
    public PushCampaigns.Audience audience(@RequestBody PushCampaigns.Draft body) {
        return campaigns.audience(require(body));
    }

    @PostMapping("/{id}/test")
    public PushCampaigns.TestResult test(@PathVariable long id) {
        return campaigns.test(id);
    }

    @PostMapping("/{id}/schedule")
    public PushCampaigns.Campaign schedule(@PathVariable long id, @RequestBody Schedule body) {
        if (body == null) throw new IllegalArgumentException("Schedule required");
        Instant at;
        try {
            at = body.scheduledAt() == null || body.scheduledAt().isBlank() ? null : Instant.parse(body.scheduledAt());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid schedule time");
        }
        return campaigns.schedule(id, at, body.revision());
    }

    @PostMapping("/{id}/cancel")
    public PushCampaigns.Campaign cancel(@PathVariable long id) {
        return campaigns.cancel(id);
    }

    private static PushCampaigns.Draft require(PushCampaigns.Draft body) {
        if (body == null) throw new IllegalArgumentException("Campaign required");
        return body;
    }
}
