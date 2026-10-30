package com.omnia.backend.controller;
import com.omnia.backend.dto.response.PlatformReportResponse;
import com.omnia.backend.service.impl.PlatformReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import org.springframework.http.*;

@RestController @RequestMapping("/api/admin/platform-reports") @PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformReportController {
    private final PlatformReportService service;
    public PlatformReportController(PlatformReportService service){this.service=service;}
    @GetMapping public ResponseEntity<PlatformReportResponse> get(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue="30") int days,@RequestParam(required=false) Long organizationId){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(start,end,days,organizationId));
    }
}
