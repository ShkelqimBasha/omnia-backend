package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.common.exception.TooManyRequestsException;
import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductReport;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.ProductReportReason;
import com.omnia.backend.enums.ProductReportStatus;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.repository.ProductReportRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.security.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Raporto njoftimin" and the platform admin's moderation queue. A decision on one report
 * (hide the product, dismiss, warn the company) closes every open report on that product.
 */
@Service
public class ProductReportService {

    /** Reports one user can send in 24 hours, across all products. */
    static final int MAX_REPORTS_PER_DAY = 10;
    static final int MAX_DETAILS = 1000;

    private final ProductReportRepository reportRepository;
    private final ProductRepository productRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    @Autowired(required = false)
    private NotificationWriter notificationWriter;

    public ProductReportService(ProductReportRepository reportRepository,
                                ProductRepository productRepository,
                                CurrentUserService currentUserService,
                                Clock clock) {
        this.reportRepository = reportRepository;
        this.productRepository = productRepository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    public record Reason(String value, String label) {
    }

    public record Created(Long id, String status) {
    }

    public record View(
            Long id,
            Long productId,
            String productName,
            String productStatus,
            Long organizationId,
            String organizationName,
            String reporter,
            String reason,
            String reasonLabel,
            String details,
            String status,
            String resolutionNote,
            String resolvedBy,
            OffsetDateTime resolvedAt,
            OffsetDateTime createdAt,
            /** Open reports on the same product, this one included. */
            long openReportsOnProduct
    ) {
    }

    public static List<Reason> reasons() {
        return java.util.Arrays.stream(ProductReportReason.values())
                .map(r -> new Reason(r.name(), r.label()))
                .toList();
    }

    @Transactional
    public Created report(Long productId, ProductReportReason reason, String rawDetails) {
        User reporter = currentUserService.requireCurrentUser();
        if (reason == null) {
            throw new IllegalArgumentException("Zgjidh arsyen e raportimit");
        }
        Product product = productRepository.findById(productId)
                .filter(p -> p.getStatus() != ProductStatus.INACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        String details = rawDetails == null || rawDetails.isBlank() ? null : rawDetails.trim();
        if (details != null && details.length() > MAX_DETAILS) {
            throw new IllegalArgumentException("Përshkrimi mund të ketë deri në 1000 karaktere");
        }
        if (reason == ProductReportReason.OTHER && details == null) {
            throw new IllegalArgumentException("Shkruaj shkurt çfarë nuk shkon me njoftimin");
        }
        if (reportRepository.existsByReporterIdAndProductIdAndStatus(
                reporter.getId(), product.getId(), ProductReportStatus.OPEN)) {
            throw new ConflictException("E ke raportuar tashmë këtë njoftim. Po e shqyrtojmë.");
        }
        LocalDateTime now = now();
        if (reportRepository.countByReporterIdAndCreatedAtAfter(reporter.getId(), now.minusDays(1))
                >= MAX_REPORTS_PER_DAY) {
            throw new TooManyRequestsException("Ke dërguar shumë raportime sot. Provo përsëri nesër.");
        }
        ProductReport saved = reportRepository.save(ProductReport.builder()
                .product(product)
                .organization(product.getOrganization())
                .reporter(reporter)
                .reason(reason)
                .details(details)
                .status(ProductReportStatus.OPEN)
                .createdAt(now)
                .build());
        return new Created(saved.getId(), saved.getStatus().name());
    }

    @Transactional(readOnly = true)
    public PagedResponse<View> queue(ProductReportStatus status, int page, int size) {
        currentUserService.requirePlatformAdmin();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 50)));
        Page<ProductReport> reports = status == null
                ? reportRepository.findAllByOrderByIdDesc(pageable)
                : reportRepository.findByStatusOrderByIdDesc(status, pageable);
        Map<Long, Long> open = new HashMap<>();
        List<Long> productIds = reports.getContent().stream().map(r -> r.getProduct().getId()).distinct().toList();
        if (!productIds.isEmpty()) {
            for (Object[] row : reportRepository.countByProducts(productIds, ProductReportStatus.OPEN)) {
                open.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }
        return PagedResponse.from(reports.map(r -> view(r, open.getOrDefault(r.getProduct().getId(), 0L))));
    }

    /** Takes the listing off the catalog (INACTIVE) and closes its open reports. */
    @Transactional
    public View hide(Long reportId, String note) {
        ProductReport report = decide(reportId);
        Product product = report.getProduct();
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.save(product);
        return close(report, ProductReportStatus.HIDDEN, note);
    }

    @Transactional
    public View dismiss(Long reportId, String note) {
        return close(decide(reportId), ProductReportStatus.DISMISSED, note);
    }

    /** Leaves the listing online and sends the company a warning notice. */
    @Transactional
    public View warn(Long reportId, String note) {
        ProductReport report = decide(reportId);
        View view = close(report, ProductReportStatus.WARNED, note);
        if (notificationWriter != null) notificationWriter.productWarning(report);
        return view;
    }

    /** Undo of "hide": the listing is ACTIVE again (e.g. the company fixed it). */
    @Transactional
    public View restore(Long reportId) {
        currentUserService.requirePlatformAdmin();
        ProductReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Raportimi nuk u gjet"));
        if (report.getStatus() != ProductReportStatus.HIDDEN) {
            throw new ConflictException("Vetëm një produkt i fshehur nga moderimi mund të rikthehet");
        }
        Product product = report.getProduct();
        if (product.getStatus() != ProductStatus.INACTIVE) {
            throw new ConflictException("Produkti është tashmë aktiv");
        }
        product.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product);
        AdminAudit.note("Produkti: " + product.getName());
        return view(report, 0);
    }

    private ProductReport decide(Long reportId) {
        currentUserService.requirePlatformAdmin();
        ProductReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Raportimi nuk u gjet"));
        if (report.getStatus() != ProductReportStatus.OPEN) {
            throw new ConflictException("Ky raportim është shqyrtuar tashmë");
        }
        return report;
    }

    private View close(ProductReport report, ProductReportStatus decision, String rawNote) {
        String note = rawNote == null || rawNote.isBlank() ? null : rawNote.trim();
        if (note != null && note.length() > 500) {
            throw new IllegalArgumentException("Shënimi mund të ketë deri në 500 karaktere");
        }
        User admin = currentUserService.requireCurrentUser();
        LocalDateTime now = now();
        for (ProductReport open : reportRepository.findByProductIdAndStatus(
                report.getProduct().getId(), ProductReportStatus.OPEN)) {
            open.setStatus(decision);
            open.setResolutionNote(note);
            open.setResolvedBy(admin);
            open.setResolvedAt(now);
            reportRepository.save(open);
        }
        AdminAudit.note("Produkti: " + report.getProduct().getName()
                + " · Arsyeja: " + report.getReason().label()
                + (note == null ? "" : " · Shënim: " + note));
        return view(report, 0);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }

    private static OffsetDateTime utc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    static View view(ProductReport r, long openOnProduct) {
        Product product = r.getProduct();
        return new View(r.getId(), product.getId(), product.getName(), product.getStatus().name(),
                r.getOrganization() == null ? null : r.getOrganization().getId(),
                r.getOrganization() == null ? null : r.getOrganization().getName(),
                r.getReporter() == null ? null : AdminActionLogService.actorLabel(r.getReporter()),
                r.getReason().name(), r.getReason().label(), r.getDetails(), r.getStatus().name(),
                r.getResolutionNote(),
                r.getResolvedBy() == null ? null : AdminActionLogService.actorLabel(r.getResolvedBy()),
                utc(r.getResolvedAt()), utc(r.getCreatedAt()), openOnProduct);
    }
}
