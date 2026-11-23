package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.ProductInquiryRequest;
import com.omnia.backend.dto.response.ProductInquiryResponse;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductInquiry;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.InquiryType;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.repository.ProductInquiryRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** "Kontakto" and "Kërko vizitë" requests on car and property listings. */
@Service
public class ProductInquiryService {

    static final int MAX_PER_PRODUCT_PER_DAY = 5;
    private static final ZoneId TIRANA = ZoneId.of("Europe/Tirane");

    private final ProductInquiryRepository inquiryRepository;
    private final ProductRepository productRepository;
    private final CurrentUserService currentUserService;
    private final OrganizationAccessService accessService;
    private final Clock clock;

    @Autowired(required = false)
    private NotificationWriter notificationWriter;

    @Autowired(required = false)
    private ProductActivityCounter activityCounter;

    @Autowired
    public ProductInquiryService(ProductInquiryRepository inquiryRepository,
                                 ProductRepository productRepository,
                                 CurrentUserService currentUserService,
                                 OrganizationAccessService accessService) {
        this(inquiryRepository, productRepository, currentUserService, accessService,
                Clock.system(TIRANA));
    }

    ProductInquiryService(ProductInquiryRepository inquiryRepository,
                          ProductRepository productRepository,
                          CurrentUserService currentUserService,
                          OrganizationAccessService accessService,
                          Clock clock) {
        this.inquiryRepository = inquiryRepository;
        this.productRepository = productRepository;
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
        ProductInquiry saved = inquiryRepository.save(ProductInquiry.builder()
                .product(product)
                .organization(product.getOrganization())
                .sender(sender)
                .type(request.getType())
                .contactName(request.getName().trim())
                .contactPhone(request.getPhone().trim())
                .message(blankToNull(request.getMessage()))
                .preferredDate(request.getType() == InquiryType.VISIT
                        ? request.getPreferredDate() : null)
                .build());
        if (notificationWriter != null) notificationWriter.inquiry(saved);
        if (activityCounter != null) {
            activityCounter.record(product.getId(), ProductActivityCounter.Metric.INQUIRY);
        }
        return toResponse(saved);
    }

    static void validate(CategoryKind kind, ProductInquiryRequest request, LocalDate today) {
        if (request.getType() == InquiryType.VISIT) {
            if (!kind.allowsVisitRequest()) {
                throw new IllegalArgumentException("Visits can only be requested for properties");
            }
            if (request.getPreferredDate() != null
                    && (request.getPreferredDate().isBefore(today)
                    || request.getPreferredDate().isAfter(today.plusDays(90)))) {
                throw new IllegalArgumentException("Choose a visit date within the next 90 days");
            }
        } else if (!kind.allowsContact()) {
            throw new IllegalArgumentException("This listing is bought through the cart");
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductInquiryResponse> forOrganization(Long organizationId, int page, int size) {
        accessService.requireCanAccessOrganization(organizationId);
        return PagedResponse.from(inquiryRepository
                .findByOrganizationIdOrderByIdDesc(organizationId,
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

    private static int clamp(int size) {
        return Math.max(1, Math.min(size, 50));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static ProductInquiryResponse toResponse(ProductInquiry inquiry) {
        return ProductInquiryResponse.builder()
                .id(inquiry.getId())
                .productId(inquiry.getProduct().getId())
                .productName(inquiry.getProduct().getName())
                .organizationId(inquiry.getOrganization() == null
                        ? null : inquiry.getOrganization().getId())
                .type(inquiry.getType().name())
                .name(inquiry.getContactName())
                .phone(inquiry.getContactPhone())
                .message(inquiry.getMessage())
                .preferredDate(inquiry.getPreferredDate())
                .status(inquiry.getStatus())
                .createdAt(inquiry.getCreatedAt())
                .build();
    }
}
