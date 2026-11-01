package com.omnia.backend.controller;
import com.omnia.backend.dto.response.CompanyReportResponse;
import com.omnia.backend.service.impl.CompanyReportService;
import org.springframework.http.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController @RequestMapping("/api/organizations/{organizationId}/report") @PreAuthorize("isAuthenticated()")
public class CompanyReportController {
    private final CompanyReportService service;
    public CompanyReportController(CompanyReportService service){this.service=service;}
    @GetMapping public ResponseEntity<CompanyReportResponse> get(@PathVariable long organizationId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate end,@RequestParam(defaultValue="30") int days){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(organizationId,start,end,days));
    }
}
