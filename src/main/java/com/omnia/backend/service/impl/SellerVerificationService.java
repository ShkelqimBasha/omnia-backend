package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.entity.OrganizationVerification;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.OrganizationMemberStatus;
import com.omnia.backend.enums.VerificationStatus;
import com.omnia.backend.repository.OrganizationMemberRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.OrganizationVerificationRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.interfaces.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * "Shitës i verifikuar": the company's owner/admin sends the NIPT and a document (PDF or photo,
 * stored as a private file); the platform admin approves or refuses it with a reason.
 */
@Service
public class SellerVerificationService {

    /** Albanian NIPT: a letter, 8 digits, a letter (e.g. K12345678A). */
    static final Pattern NIPT = Pattern.compile("^[A-Z][0-9]{8}[A-Z]$");

    private final OrganizationVerificationRepository verificationRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;
    private final Clock clock;

    @Autowired(required = false)
    private NotificationWriter notificationWriter;

    public SellerVerificationService(OrganizationVerificationRepository verificationRepository,
                                     OrganizationRepository organizationRepository,
                                     OrganizationMemberRepository memberRepository,
                                     CurrentUserService currentUserService,
                                     FileStorageService fileStorageService,
                                     Clock clock) {
        this.verificationRepository = verificationRepository;
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.currentUserService = currentUserService;
        this.fileStorageService = fileStorageService;
        this.clock = clock;
    }

    public record View(
            Long id,
            Long organizationId,
            String organizationName,
            boolean organizationVerified,
            String nipt,
            String status,
            String rejectionReason,
            String submittedBy,
            OffsetDateTime submittedAt,
            String reviewedBy,
            OffsetDateTime reviewedAt,
            String documentName,
            String documentContentType,
            Long documentSize,
            /** Platform admins only; null for the company. */
            String documentUrl
    ) {
    }

    /** What the company sees on its profile page. */
    public record CompanyStatus(
            Long organizationId,
            boolean verified,
            OffsetDateTime verifiedAt,
            boolean canSubmit,
            View latest
    ) {
    }

    public record Document(Resource resource, String filename, String contentType, long size) {
    }

    @Transactional
    public View submit(Long organizationId, String rawNipt, MultipartFile document) {
        User user = currentUserService.requireCurrentUser();
        Organization organization = findOrganization(organizationId);
        requireCompanyManager(user, organization);
        String nipt = normalizeNipt(rawNipt);
        if (organization.isVerified()) {
            throw new ConflictException("Kompania është tashmë e verifikuar");
        }
        if (verificationRepository.existsByOrganizationIdAndStatus(organizationId, VerificationStatus.PENDING)) {
            throw new ConflictException("Kërkesa e verifikimit është ende në shqyrtim");
        }
        if (document == null || document.isEmpty()) {
            throw new IllegalArgumentException("Ngarko dokumentin e regjistrimit (PDF ose foto)");
        }
        UploadedFile file = fileStorageService.storePrivateDocument(document);
        OrganizationVerification saved = verificationRepository.save(OrganizationVerification.builder()
                .organization(organization)
                .nipt(nipt)
                .documentFile(file)
                .status(VerificationStatus.PENDING)
                .submittedBy(user)
                .submittedAt(now())
                .build());
        AdminAudit.note("Kompania: " + organization.getName() + " · NIPT: " + nipt);
        return view(saved, false);
    }

    @Transactional(readOnly = true)
    public CompanyStatus companyStatus(Long organizationId) {
        User user = currentUserService.requireCurrentUser();
        Organization organization = findOrganization(organizationId);
        boolean admin = currentUserService.hasPlatformAdminAccess(user);
        if (!admin) {
            requireActiveMember(user, organization.getId());
        }
        View latest = verificationRepository.findFirstByOrganizationIdOrderByIdDesc(organizationId)
                .map(v -> view(v, admin))
                .orElse(null);
        boolean pending = latest != null && VerificationStatus.PENDING.name().equals(latest.status());
        return new CompanyStatus(organizationId, organization.isVerified(),
                utc(organization.getVerifiedAt()), !organization.isVerified() && !pending, latest);
    }

    @Transactional(readOnly = true)
    public PagedResponse<View> adminList(VerificationStatus status, int page, int size) {
        currentUserService.requirePlatformAdmin();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 50)));
        return PagedResponse.from((status == null
                ? verificationRepository.findAllByOrderByIdDesc(pageable)
                : verificationRepository.findByStatusOrderByIdDesc(status, pageable))
                .map(v -> view(v, true)));
    }

    @Transactional
    public View approve(Long id) {
        currentUserService.requirePlatformAdmin();
        OrganizationVerification verification = findPending(id);
        LocalDateTime now = now();
        verification.setStatus(VerificationStatus.APPROVED);
        verification.setRejectionReason(null);
        verification.setReviewedBy(currentUserService.requireCurrentUser());
        verification.setReviewedAt(now);
        Organization organization = verification.getOrganization();
        organization.setVerifiedAt(now);
        organizationRepository.save(organization);
        verificationRepository.save(verification);
        AdminAudit.note("Kompania: " + organization.getName() + " · NIPT: " + verification.getNipt());
        if (notificationWriter != null) notificationWriter.verification(verification);
        return view(verification, true);
    }

    @Transactional
    public View reject(Long id, String rawReason) {
        currentUserService.requirePlatformAdmin();
        String reason = requireReason(rawReason);
        OrganizationVerification verification = findPending(id);
        verification.setStatus(VerificationStatus.REJECTED);
        verification.setRejectionReason(reason);
        verification.setReviewedBy(currentUserService.requireCurrentUser());
        verification.setReviewedAt(now());
        verificationRepository.save(verification);
        AdminAudit.note("Kompania: " + verification.getOrganization().getName() + " · Arsyeja: " + reason);
        if (notificationWriter != null) notificationWriter.verification(verification);
        return view(verification, true);
    }

    /** Takes the badge away (e.g. the company closed); the company can then apply again. */
    @Transactional
    public CompanyStatus revoke(Long organizationId, String rawReason) {
        currentUserService.requirePlatformAdmin();
        String reason = requireReason(rawReason);
        Organization organization = findOrganization(organizationId);
        if (!organization.isVerified()) {
            throw new ConflictException("Kompania nuk është e verifikuar");
        }
        organization.setVerifiedAt(null);
        organizationRepository.save(organization);
        AdminAudit.note("Kompania: " + organization.getName() + " · Arsyeja: " + reason);
        return new CompanyStatus(organizationId, false, null, true,
                verificationRepository.findFirstByOrganizationIdOrderByIdDesc(organizationId)
                        .map(v -> view(v, true)).orElse(null));
    }

    @Transactional(readOnly = true)
    public Document document(Long id) {
        currentUserService.requirePlatformAdmin();
        OrganizationVerification verification = verificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kërkesa e verifikimit nuk u gjet"));
        UploadedFile file = verification.getDocumentFile();
        if (file == null) {
            throw new ResourceNotFoundException("Dokumenti nuk u gjet");
        }
        return new Document(fileStorageService.loadFileAsResource(file.getId()),
                file.getOriginalName(), file.getContentType(), file.getSize());
    }

    static String normalizeNipt(String raw) {
        String nipt = raw == null ? "" : raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!NIPT.matcher(nipt).matches()) {
            throw new IllegalArgumentException("NIPT duhet të ketë formatin K12345678A");
        }
        return nipt;
    }

    static String requireReason(String raw) {
        String reason = raw == null ? "" : raw.trim();
        if (reason.length() < 3) {
            throw new IllegalArgumentException("Shkruaj arsyen");
        }
        if (reason.length() > 500) {
            throw new IllegalArgumentException("Arsyeja mund të ketë deri në 500 karaktere");
        }
        return reason;
    }

    private OrganizationVerification findPending(Long id) {
        OrganizationVerification verification = verificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kërkesa e verifikimit nuk u gjet"));
        if (verification.getStatus() != VerificationStatus.PENDING) {
            throw new ConflictException("Kjo kërkesë është shqyrtuar tashmë");
        }
        return verification;
    }

    private Organization findOrganization(Long organizationId) {
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("Organization id must be positive");
        }
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }

    private void requireCompanyManager(User user, Organization organization) {
        if (currentUserService.hasPlatformAdminAccess(user)) {
            return;
        }
        OrganizationMember member = requireActiveMember(user, organization.getId());
        if (!member.canManageProducts() || !organization.isActive()) {
            throw new AccessDeniedException("Only the company owner or admin can request verification");
        }
    }

    private OrganizationMember requireActiveMember(User user, Long organizationId) {
        return memberRepository.findByOrganizationIdAndUserIdAndStatus(
                        organizationId, user.getId(), OrganizationMemberStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("You are not an active member of this organization"));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }

    private static OffsetDateTime utc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    static View view(OrganizationVerification v, boolean admin) {
        Organization organization = v.getOrganization();
        UploadedFile file = v.getDocumentFile();
        return new View(v.getId(), organization.getId(), organization.getName(), organization.isVerified(),
                v.getNipt(), v.getStatus().name(), v.getRejectionReason(),
                AdminActionLogService.actorLabel(v.getSubmittedBy()), utc(v.getSubmittedAt()),
                v.getReviewedBy() == null ? null : AdminActionLogService.actorLabel(v.getReviewedBy()),
                utc(v.getReviewedAt()),
                file == null ? null : file.getOriginalName(),
                file == null ? null : file.getContentType(),
                file == null ? null : file.getSize(),
                admin && file != null ? "/api/admin/verifications/" + v.getId() + "/document" : null);
    }
}
