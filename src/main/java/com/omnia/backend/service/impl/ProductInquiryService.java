package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.ProductInquiryRequest;
import com.omnia.backend.dto.response.ProductInquiryResponse;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductInquiry;
import com.omnia.backend.entity.ProductVisitSlot;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.InquiryStatus;
import com.omnia.backend.enums.InquiryType;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.event.InquiryCreatedEvent;
import com.omnia.backend.repository.ProductInquiryRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.ProductVisitSlotRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** "Kontakto" and "Kërko vizitë" requests on car and property listings, and their status workflow. */
@Service
public class ProductInquiryService {

    static final int MAX_PER_PRODUCT_PER_DAY = 5;
    static final ZoneId TIRANA = ZoneId.of("Europe/Tirane");

    private final ProductInquiryRepository inquiryRepository;
    private final ProductRepository productRepository;
    private final ProductVisitSlotRepository slotRepository;
    private final CurrentUserService currentUserService;
    private final OrganizationAccessService accessService;
    private final Clock clock;

    @Autowired(required = false)
    private NotificationWriter notificationWriter;

    @Autowired(required = false)
    private ApplicationEventPublisher events;

    @Autowired(required = false)
    private JdbcTemplate jdbc;

    @Autowired(required = false)
    private ProductActivityCounter activityCounter;

    @Autowired
    public ProductInquiryService(ProductInquiryRepository inquiryRepository,
                                 ProductRepository productRepository,
                                 ProductVisitSlotRepository slotRepository,
                                 CurrentUserService currentUserService,
                                 OrganizationAccessService accessService) {
        this(inquiryRepository, productRepository, slotRepository, currentUserService, accessService,
                Clock.system(TIRANA));
    }

    ProductInquiryService(ProductInquiryRepository inquiryRepository,
                          ProductRepository productRepository,
                          ProductVisitSlotRepository slotRepository,
                          CurrentUserService currentUserService,
                          OrganizationAccessService accessService,
                          Clock clock) {
        this.inquiryRepository = inquiryRepository;
        this.productRepository = productRepository;
        this.slotRepository = slotRepository;
        this.currentUserService = currentUserService;
        this.accessService = accessService;
        this.clock = clock;
    }

    @Transactional
    public ProductInquiryResponse create(Long productId, ProductInquiryRequest request) {
        User sender = currentUserService.requireCurrentUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ResourceNotFoundException("Product not found");
        }
        CategoryKind kind = CategoryKind.fromNullable(product.getCategory().getKind());
        validate(kind, request, LocalDate.now(clock));
        if (inquiryRepository.countRecent(sender.getId(), product.getId()) >= MAX_PER_PRODUCT_PER_DAY) {
            throw new IllegalArgumentException(
                    "Too many requests for this listing today. Try again tomorrow.");
        }

        ProductVisitSlot slot = null;
        if (request.getType() == InquiryType.VISIT && request.getVisitSlotId() != null) {
            // Locked until commit, so two buyers can't book the same time.
            slot = slotRepository.lockForBooking(request.getVisitSlotId(), product.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Visit slot not found"));
            if (slot.getInquiry() != null || !slot.getStartsAt().isAfter(LocalDateTime.now(clock))) {
                throw new ConflictException("This visit time is no longer available. Choose another one.");
            }
        }

        ProductInquiry saved = inquiryRepository.save(ProductInquiry.builder()
                .product(product)
                .organization(product.getOrganization())
                .sender(sender)
                .type(request.getType())
                .contactName(request.getName().trim())
                .contactPhone(request.getPhone().trim())
                .message(blankToNull(request.getMessage()))
                .preferredDate(request.getType() != InquiryType.VISIT ? null
                        : slot != null ? slot.getStartsAt().toLocalDate() : request.getPreferredDate())
                .visitAt(slot == null ? null : slot.getStartsAt())
                .build());
        if (slot != null) {
            slot.setInquiry(saved);
            slotRepository.save(slot);
        }
        if (notificationWriter != null) notificationWriter.inquiry(saved);
        if (activityCounter != null) {
            activityCounter.record(product.getId(), ProductActivityCounter.Metric.INQUIRY);
        }
        if (events != null) {
            Organization company = product.getOrganization();
            List<String> recipients = companyEmails(company);
            if (!recipients.isEmpty()) {
                events.publishEvent(new InquiryCreatedEvent(saved.getId(), recipients,
                        company.getName(), product.getName(), product.getId(), saved.getType().name(),
                        saved.getContactName(), saved.getContactPhone(), saved.getMessage(),
                        saved.getPreferredDate(), saved.getVisitAt()));
            }
        }
        return toResponse(saved);
    }

    static void validate(CategoryKind kind, ProductInquiryRequest request, LocalDate today) {
        if (request.getType() == InquiryType.VISIT) {
            if (!kind.allowsVisitRequest()) {
                throw new IllegalArgumentException("Visits can only be requested for properties");
            }
            if (request.getVisitSlotId() == null && request.getPreferredDate() != null
                    && (request.getPreferredDate().isBefore(today)
                    || request.getPreferredDate().isAfter(today.plusDays(90)))) {
                throw new IllegalArgumentException("Choose a visit date within the next 90 days");
            }
        } else {
            if (!kind.allowsContact()) {
                throw new IllegalArgumentException("This listing is bought through the cart");
            }
            if (request.getVisitSlotId() != null) {
                throw new IllegalArgumentException("A visit time can only be chosen for a visit request");
            }
        }
    }

    /** The company's business email, or its active owners' emails when it has none. */
    private List<String> companyEmails(Organization company) {
        List<String> emails = new ArrayList<>();
        if (company == null) return emails;
        if (company.getBusinessEmail() != null && !company.getBusinessEmail().isBlank()) {
            emails.add(company.getBusinessEmail().strip());
        } else if (jdbc != null && company.getId() != null) {
            emails.addAll(jdbc.queryForList("SELECT u.email FROM organization_members m "
                    + "JOIN users u ON u.id = m.user_id WHERE m.organization_id = ? "
                    + "AND m.membership_role = 'OWNER' AND m.status = 'ACTIVE' "
                    + "AND u.deleted_at IS NULL AND u.status = 'ACTIVE' AND u.email IS NOT NULL",
                    String.class, company.getId()));
        }
        return emails;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductInquiryResponse> forOrganization(Long organizationId, String status,
                                                                 int page, int size) {
        accessService.requireCanAccessOrganization(organizationId);
        InquiryStatus filter = parseStatus(status);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clamp(size));
        return PagedResponse.from((filter == null
                ? inquiryRepository.findByOrganizationIdOrderByIdDesc(organizationId, pageable)
                : inquiryRepository.findByOrganizationIdAndStatusOrderByIdDesc(organizationId, filter, pageable))
                .map(ProductInquiryService::toResponse));
    }

    /**
     * The seller moves an inquiry along: E re → U kontaktua → Vizitë e caktuar → Mbyllur.
     * Any step may be chosen (also back, to fix a mistake). Closing frees a future visit slot it booked.
     */
    @Transactional
    public ProductInquiryResponse updateStatus(Long organizationId, Long inquiryId, InquiryStatus status) {
        if (status == null) throw new IllegalArgumentException("Status is required");
        accessService.requireCanManageOrders(organizationId);
        ProductInquiry inquiry = inquiryRepository.findById(inquiryId)
                .filter(i -> i.getOrganization() != null && organizationId.equals(i.getOrganization().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found"));
        if (inquiry.getStatus() != status) {
            inquiry.setStatus(status);
            inquiry.setStatusUpdatedAt(LocalDateTime.now(clock));
            if (status == InquiryStatus.CLOSED) {
                slotRepository.findByInquiryId(inquiry.getId())
                        .filter(slot -> slot.getStartsAt().isAfter(LocalDateTime.now(clock)))
                        .ifPresent(slot -> slot.setInquiry(null));
            }
        }
        return toResponse(inquiry);
    }

    /** Platform admin: all companies' inquiries, filtered by status, type, category kind and company. */
    @Transactional(readOnly = true)
    public PagedResponse<ProductInquiryResponse> forPlatform(String status, String type, String kind,
                                                             Long organizationId, int page, int size) {
        currentUserService.requirePlatformAdmin();
        return PagedResponse.from(inquiryRepository.search(parseStatus(status), parseEnum(InquiryType.class, type),
                        parseEnum(CategoryKind.class, kind), organizationId,
                        PageRequest.of(Math.max(page, 0), clamp(size)))
                .map(ProductInquiryService::toResponse));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductInquiryResponse> mine(int page, int size) {
        User user = currentUserService.requireCurrentUser();
        return PagedResponse.from(inquiryRepository
                .findBySenderIdOrderByIdDesc(user.getId(),
                        PageRequest.of(Math.max(page, 0), clamp(size)))
                .map(ProductInquiryService::toResponse));
    }

    private static InquiryStatus parseStatus(String value) {
        return parseEnum(InquiryStatus.class, value);
    }

    static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + value);
        }
    }

    private static int clamp(int size) {
        return Math.max(1, Math.min(size, 50));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static ProductInquiryResponse toResponse(ProductInquiry inquiry) {
        Product product = inquiry.getProduct();
        return ProductInquiryResponse.builder()
                .id(inquiry.getId())
                .productId(product.getId())
                .productName(product.getName())
                .organizationId(inquiry.getOrganization() == null
                        ? null : inquiry.getOrganization().getId())
                .organizationName(inquiry.getOrganization() == null
                        ? null : inquiry.getOrganization().getName())
                .categoryKind(product.getCategory() == null
                        ? null : CategoryKind.fromNullable(product.getCategory().getKind()).name())
                .type(inquiry.getType().name())
                .name(inquiry.getContactName())
                .phone(inquiry.getContactPhone())
                .message(inquiry.getMessage())
                .preferredDate(inquiry.getPreferredDate())
                .visitAt(inquiry.getVisitAt())
                .status(inquiry.getStatus().name())
                .statusUpdatedAt(inquiry.getStatusUpdatedAt())
                .createdAt(inquiry.getCreatedAt())
                .build();
    }
}
