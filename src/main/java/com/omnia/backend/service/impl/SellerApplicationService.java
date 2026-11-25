package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.SellerApplicationRequest;
import com.omnia.backend.dto.response.SellerApplicationResponse;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.OrganizationCategoryPermission;
import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.entity.SellerApplication;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.enums.OrganizationMemberRole;
import com.omnia.backend.enums.OrganizationMemberStatus;
import com.omnia.backend.enums.OrganizationPermissionStatus;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.enums.SellerApplicationStatus;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.event.SellerApplicationDecidedEvent;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.OrganizationCategoryPermissionRepository;
import com.omnia.backend.repository.OrganizationMemberRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.SellerApplicationRepository;
import com.omnia.backend.security.service.CurrentUserService;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Bëhu shitës": a logged-in user applies to sell (as a business with NIPT or as a private person);
 * a platform admin approves, which creates the store (organization, OWNER membership, category
 * permissions) in one transaction, or rejects with a reason. A rejected user may apply again.
 */
@Service
public class SellerApplicationService {

    static final String PENDING_EXISTS = "Ke tashmë një aplikim në shqyrtim.";
    static final String ALREADY_OWNER = "Ke tashmë një dyqan në Omnia.";
    static final String APPROVED_LABEL = "Aplikimi u pranua — dyqani yt është gati.";

    private final SellerApplicationRepository applicationRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationCategoryPermissionRepository permissionRepository;
    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;
    private final ApplicationEventPublisher events;
    private final EntityManager entityManager;
    private final Clock clock;

    @Autowired(required = false)
    private NotificationWriter notificationWriter;

    public SellerApplicationService(SellerApplicationRepository applicationRepository,
                                    OrganizationRepository organizationRepository,
                                    OrganizationMemberRepository memberRepository,
                                    OrganizationCategoryPermissionRepository permissionRepository,
                                    CategoryRepository categoryRepository,
                                    CurrentUserService currentUserService,
                                    ApplicationEventPublisher events,
                                    EntityManager entityManager,
                                    Clock clock) {
        this.applicationRepository = applicationRepository;
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.permissionRepository = permissionRepository;
        this.categoryRepository = categoryRepository;
        this.currentUserService = currentUserService;
        this.events = events;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public SellerApplicationResponse apply(SellerApplicationRequest request) {
        User user = currentUserService.requireCurrentUser();
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Llogaria jote nuk është aktive.");
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("Verifiko email-in para se të aplikosh si shitës.");
        }
        SellerApplicationRules.Normalized input = SellerApplicationRules.normalize(request);
        List<Category> categories = requireTopLevelCategories(input.categoryIds());

        // One applicant at a time: hold the user row so two quick submits cannot both pass the checks.
        entityManager.createNativeQuery("SELECT id FROM users WHERE id = ? FOR UPDATE")
                .setParameter(1, user.getId())
                .getResultList();
        if (applicationRepository.existsByUserIdAndStatus(user.getId(), SellerApplicationStatus.PENDING)) {
            throw new IllegalArgumentException(PENDING_EXISTS);
        }
        if (isActiveOwner(user.getId())) {
            throw new IllegalArgumentException(ALREADY_OWNER);
        }

        LocalDateTime now = now();
        SellerApplication saved = applicationRepository.saveAndFlush(SellerApplication.builder()
                .user(user)
                .sellerType(input.sellerType())
                .storeName(input.storeName())
                .phone(input.phone())
                .city(input.city())
                .nipt(input.nipt())
                .categoryIds(SellerApplicationRules.joinIds(input.categoryIds()))
                .description(input.description())
                .status(SellerApplicationStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build());
        if (notificationWriter != null) notificationWriter.sellerApplicationSubmitted(saved);
        return response(saved, names(categories));
    }

    @Transactional(readOnly = true)
    public Optional<SellerApplicationResponse> mine() {
        User user = currentUserService.requireCurrentUser();
        return applicationRepository.findFirstByUserIdOrderByIdDesc(user.getId())
                .map(a -> response(a, names(categoryRepository.findAllById(
                        SellerApplicationRules.splitIds(a.getCategoryIds())))));
    }

    @Transactional(readOnly = true)
    public PagedResponse<SellerApplicationResponse> adminList(SellerApplicationStatus status, int page, int size) {
        currentUserService.requirePlatformAdmin();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 50)));
        Page<SellerApplication> found = status == null
                ? applicationRepository.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : applicationRepository.findByStatusOrderByCreatedAtDescIdDesc(status, pageable);
        Set<Long> ids = new HashSet<>();
        found.forEach(a -> ids.addAll(SellerApplicationRules.splitIds(a.getCategoryIds())));
        Map<Long, String> names = names(categoryRepository.findAllById(ids));
        return PagedResponse.from(found.map(a -> response(a, names)));
    }

    @Transactional
    public SellerApplicationResponse approve(Long id) {
        currentUserService.requirePlatformAdmin();
        User admin = currentUserService.requireCurrentUser();
        SellerApplication application = findPending(id);
        User applicant = application.getUser();
        if (isActiveOwner(applicant.getId())) {
            throw new ConflictException("Aplikuesi ka tashmë një dyqan në Omnia.");
        }
        List<Long> categoryIds = SellerApplicationRules.splitIds(application.getCategoryIds());
        List<Category> categories = requireTopLevelCategories(categoryIds);

        Organization organization;
        try {
            String name = uniqueName(application.getStoreName());
            organization = organizationRepository.saveAndFlush(Organization.builder()
                    .name(name)
                    .editorNameKey(name.toLowerCase(Locale.ROOT))
                    .slug(uniqueSlug(name))
                    .description(application.getDescription())
                    .status(OrganizationStatus.ACTIVE)
                    .sellerType(application.getSellerType())
                    .phone(application.getPhone())
                    .address(application.getCity())
                    .createdBy(admin)
                    .primaryAdministrator(applicant)
                    .build());
            memberRepository.saveAndFlush(OrganizationMember.builder()
                    .organization(organization)
                    .user(applicant)
                    .membershipRole(OrganizationMemberRole.OWNER)
                    .status(OrganizationMemberStatus.ACTIVE)
                    .createdBy(admin)
                    .build());
            for (Category category : categories) {
                permissionRepository.save(OrganizationCategoryPermission.builder()
                        .organization(organization)
                        .category(category)
                        .canCreate(true)
                        .canUpdate(true)
                        .canDelete(true)
                        .status(OrganizationPermissionStatus.ACTIVE)
                        .grantedBy(admin)
                        .build());
            }
            permissionRepository.flush();
        } catch (DataIntegrityViolationException conflict) {
            throw new ConflictException("Dyqani nuk u krijua: emri ose adresa e dyqanit përplaset me një tjetër. Provo përsëri.");
        }

        LocalDateTime now = now();
        application.setStatus(SellerApplicationStatus.APPROVED);
        application.setRejectReason(null);
        application.setOrganization(organization);
        application.setReviewedBy(admin);
        application.setReviewedAt(now);
        application.setUpdatedAt(now);
        applicationRepository.saveAndFlush(application);

        AdminAudit.note("Dyqani: " + organization.getName() + " · Lloji: " + application.getSellerType().name()
                + " · Aplikuesi: " + AdminActionLogService.actorLabel(applicant));
        if (notificationWriter != null) {
            notificationWriter.company(organization);
            notificationWriter.sellerApplicationDecision(application, APPROVED_LABEL);
        }
        events.publishEvent(new SellerApplicationDecidedEvent(application.getId(), applicant.getEmail(),
                displayName(applicant), organization.getName(), true, null));
        return response(application, names(categories));
    }

    @Transactional
    public SellerApplicationResponse reject(Long id, String rawReason) {
        currentUserService.requirePlatformAdmin();
        String reason = SellerApplicationRules.rejectReason(rawReason);
        User admin = currentUserService.requireCurrentUser();
        SellerApplication application = findPending(id);
        LocalDateTime now = now();
        application.setStatus(SellerApplicationStatus.REJECTED);
        application.setRejectReason(reason);
        application.setReviewedBy(admin);
        application.setReviewedAt(now);
        application.setUpdatedAt(now);
        applicationRepository.saveAndFlush(application);

        User applicant = application.getUser();
        AdminAudit.note("Dyqani: " + application.getStoreName() + " · Arsyeja: " + reason);
        if (notificationWriter != null) {
            notificationWriter.sellerApplicationDecision(application, "Aplikimi u refuzua: " + reason);
        }
        events.publishEvent(new SellerApplicationDecidedEvent(application.getId(), applicant.getEmail(),
                displayName(applicant), application.getStoreName(), false, reason));
        return response(application, names(categoryRepository.findAllById(
                SellerApplicationRules.splitIds(application.getCategoryIds()))));
    }

    private SellerApplication findPending(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Aplikimi nuk është i vlefshëm.");
        }
        SellerApplication application = applicationRepository.findForReview(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aplikimi nuk u gjet."));
        if (application.getStatus() != SellerApplicationStatus.PENDING) {
            throw new ConflictException("Ky aplikim është shqyrtuar tashmë.");
        }
        return application;
    }

    private boolean isActiveOwner(Long userId) {
        return memberRepository
                .findAllByUserIdAndStatusOrderByOrganizationNameAsc(userId, OrganizationMemberStatus.ACTIVE)
                .stream()
                .anyMatch(m -> m.getMembershipRole() == OrganizationMemberRole.OWNER);
    }

    /** Every id must be an ACTIVE top-level category; returned in the requested order. */
    private List<Category> requireTopLevelCategories(List<Long> ids) {
        Map<Long, Category> found = categoryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        List<Category> ordered = ids.stream().map(found::get).toList();
        boolean valid = ordered.stream().allMatch(c -> c != null
                && c.getStatus() == CategoryStatus.ACTIVE
                && c.getParent() == null);
        if (!valid || ordered.isEmpty()) {
            throw new IllegalArgumentException("Zgjidh vetëm kategori kryesore aktive.");
        }
        return ordered;
    }

    /** Same as an admin-created company would read: "Butiku", else "Butiku (2)", "Butiku (3)"… */
    private String uniqueName(String storeName) {
        for (int attempt = 1; attempt < 1000; attempt++) {
            String candidate = SellerApplicationRules.numberedName(storeName, attempt);
            if (!organizationRepository.existsByNameIgnoreCaseAndIdNot(candidate, 0L)) {
                return candidate;
            }
        }
        throw new ConflictException("Nuk u gjet një emër i lirë për dyqanin.");
    }

    /** The slug rule of OrganizationServiceImpl: ascii, lowercase, dashes, "-2", "-3"… when taken. */
    private String uniqueSlug(String name) {
        String base = slugBase(name);
        String candidate = base;
        int suffix = 2;
        while (organizationRepository.existsBySlugIgnoreCase(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    static String slugBase(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (base.length() > 150) {
            base = base.substring(0, 150).replaceAll("-+$", "");
        }
        return base.isBlank() ? "organization" : base;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }

    private static OffsetDateTime utc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private static Map<Long, String> names(Collection<Category> categories) {
        return categories.stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    }

    static String displayName(User user) {
        if (user == null) {
            return null;
        }
        String name = ((user.getFirstName() == null ? "" : user.getFirstName().trim()) + " "
                + (user.getLastName() == null ? "" : user.getLastName().trim())).trim();
        return name.isEmpty() ? user.getUsername() : name;
    }

    static SellerApplicationResponse response(SellerApplication a, Map<Long, String> categoryNames) {
        List<Long> ids = SellerApplicationRules.splitIds(a.getCategoryIds());
        User user = a.getUser();
        return new SellerApplicationResponse(
                a.getId(),
                a.getSellerType().name(),
                a.getStoreName(),
                a.getPhone(),
                a.getCity(),
                a.getNipt(),
                ids,
                ids.stream().map(categoryNames::get).filter(Objects::nonNull).toList(),
                a.getDescription(),
                a.getStatus().name(),
                a.getRejectReason(),
                a.getOrganization() == null ? null : a.getOrganization().getId(),
                utc(a.getCreatedAt()),
                utc(a.getReviewedAt()),
                user == null ? null : new SellerApplicationResponse.Applicant(
                        user.getId(), displayName(user), user.getEmail()));
    }
}
