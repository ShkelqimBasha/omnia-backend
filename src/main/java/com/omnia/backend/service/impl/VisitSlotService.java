package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.response.VisitSlotResponse;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductVisitSlot;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.ProductVisitSlotRepository;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** The visit times a property seller offers; a buyer picks one in "Kërko vizitë". */
@Service
public class VisitSlotService {

    static final int MAX_UPCOMING_SLOTS = 60;
    static final int MAX_DAYS_AHEAD = 90;

    private final ProductVisitSlotRepository slotRepository;
    private final ProductRepository productRepository;
    private final OrganizationAccessService accessService;
    private final Clock clock;

    @Autowired
    public VisitSlotService(ProductVisitSlotRepository slotRepository, ProductRepository productRepository,
                            OrganizationAccessService accessService) {
        this(slotRepository, productRepository, accessService, Clock.system(ProductInquiryService.TIRANA));
    }

    VisitSlotService(ProductVisitSlotRepository slotRepository, ProductRepository productRepository,
                     OrganizationAccessService accessService, Clock clock) {
        this.slotRepository = slotRepository;
        this.productRepository = productRepository;
        this.accessService = accessService;
        this.clock = clock;
    }

    /** Public: free upcoming times of an active property listing (no buyer details). */
    @Transactional(readOnly = true)
    public List<VisitSlotResponse> available(Long productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (!isProperty(product)) return List.of();
        LocalDateTime now = LocalDateTime.now(clock);
        return slotRepository.findAvailable(product.getId(), now, now.plusDays(MAX_DAYS_AHEAD)).stream()
                .map(slot -> VisitSlotResponse.builder().id(slot.getId()).startsAt(slot.getStartsAt()).build())
                .toList();
    }

    /** Seller: every upcoming slot with who booked it. */
    @Transactional(readOnly = true)
    public List<VisitSlotResponse> upcoming(Long productId) {
        Product product = requireManagedProperty(productId);
        return slotRepository.findUpcoming(product.getId(), LocalDateTime.now(clock)).stream()
                .map(VisitSlotService::toSellerResponse)
                .toList();
    }

    @Transactional
    public VisitSlotResponse add(Long productId, LocalDateTime startsAt) {
        Product product = requireManagedProperty(productId);
        LocalDateTime time = startsAt.truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!time.isAfter(now) || time.isAfter(now.plusDays(MAX_DAYS_AHEAD))) {
            throw new IllegalArgumentException("Choose a visit time within the next 90 days");
        }
        if (slotRepository.existsByProductIdAndStartsAt(product.getId(), time)) {
            throw new ConflictException("This visit time already exists");
        }
        if (slotRepository.countUpcoming(product.getId(), now) >= MAX_UPCOMING_SLOTS) {
            throw new IllegalArgumentException("A listing can have at most " + MAX_UPCOMING_SLOTS
                    + " upcoming visit times");
        }
        return toSellerResponse(slotRepository.save(ProductVisitSlot.builder()
                .product(product).startsAt(time).build()));
    }

    @Transactional
    public void delete(Long productId, Long slotId) {
        Product product = requireManagedProperty(productId);
        ProductVisitSlot slot = slotRepository.findById(slotId)
                .filter(s -> s.getProduct().getId().equals(product.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Visit slot not found"));
        if (slot.getInquiry() != null) {
            throw new ConflictException("A buyer booked this visit time. Close the request first.");
        }
        slotRepository.delete(slot);
    }

    private Product requireManagedProperty(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (product.getOrganization() == null) {
            throw new ResourceNotFoundException("Product not found");
        }
        accessService.requireCanUpdateProduct(product.getOrganization().getId(), product.getCategory().getId());
        if (!isProperty(product)) {
            throw new IllegalArgumentException("Visit times can only be set for properties");
        }
        return product;
    }

    private static boolean isProperty(Product product) {
        return product.getCategory() != null
                && CategoryKind.fromNullable(product.getCategory().getKind()).allowsVisitRequest();
    }

    private static VisitSlotResponse toSellerResponse(ProductVisitSlot slot) {
        return VisitSlotResponse.builder()
                .id(slot.getId())
                .startsAt(slot.getStartsAt())
                .booked(slot.getInquiry() != null)
                .inquiryId(slot.getInquiry() == null ? null : slot.getInquiry().getId())
                .bookedBy(slot.getInquiry() == null ? null : slot.getInquiry().getContactName())
                .build();
    }
}
