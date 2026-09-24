package com.omnia.backend.service.impl;

import com.omnia.backend.dto.response.OrganizationStatisticsResponse;
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.enums.PaymentStatus;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.repository.OrderItemRepository;
import com.omnia.backend.repository.OrderRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.PaymentRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.ReviewRepository;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    private static final long ORGANIZATION_ID = 10L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrganizationAccessService
            organizationAccessService;

    @InjectMocks
    private StatisticsService statisticsService;

    @Test
    void organizationStatisticsShouldUseOrganizationScope() {
        when(organizationRepository.existsById(
                ORGANIZATION_ID
        )).thenReturn(true);

        when(productRepository.countByOrganizationId(
                ORGANIZATION_ID
        )).thenReturn(8L);

        when(productRepository
                .countByOrganizationIdAndStatus(
                        ORGANIZATION_ID,
                        ProductStatus.ACTIVE
                ))
                .thenReturn(6L);

        when(productRepository
                .countByOrganizationIdAndStatusAndStockLessThan(
                        ORGANIZATION_ID,
                        ProductStatus.ACTIVE,
                        5
                ))
                .thenReturn(2L);

        when(orderRepository.countByOrganizationId(
                ORGANIZATION_ID
        )).thenReturn(4L);

        when(orderRepository
                .countByOrganizationIdAndStatusNotIn(
                        eq(ORGANIZATION_ID),
                        anyCollection()
                ))
                .thenReturn(3L);

        when(paymentRepository
                .sumRevenueByOrganizationIdAndStatus(
                        ORGANIZATION_ID,
                        PaymentStatus.SUCCESS
                ))
                .thenReturn(
                        new BigDecimal("125.50")
                );

        OrganizationStatisticsResponse response =
                statisticsService
                        .getOrganizationStatistics(
                                ORGANIZATION_ID
                        );

        assertEquals(8L, response.getTotalProducts());
        assertEquals(6L, response.getActiveProducts());
        assertEquals(2L, response.getLowStockProducts());
        assertEquals(4L, response.getTotalOrders());
        assertEquals(3L, response.getActiveOrders());
        assertEquals(
                new BigDecimal("125.50"),
                response.getTotalRevenue()
        );

        verify(organizationAccessService)
                .requireCanAccessOrganization(
                        ORGANIZATION_ID
                );
    }

    @Test
    void platformStatisticsShouldUseGlobalQueries() {
        when(productRepository.count())
                .thenReturn(20L);

        when(productRepository.countByStatus(
                ProductStatus.ACTIVE
        )).thenReturn(15L);

        when(productRepository
                .countByStatusAndStockLessThan(
                        ProductStatus.ACTIVE,
                        5
                ))
                .thenReturn(3L);

        when(orderRepository.count())
                .thenReturn(12L);

        when(orderRepository.countByStatusNotIn(
                anyCollection()
        )).thenReturn(5L);

        when(paymentRepository.sumRevenueByStatus(
                PaymentStatus.SUCCESS
        )).thenReturn(
                new BigDecimal("950.00")
        );

        OrganizationStatisticsResponse response =
                statisticsService
                        .getPlatformStatistics();

        assertEquals(20L, response.getTotalProducts());
        assertEquals(15L, response.getActiveProducts());
        assertEquals(3L, response.getLowStockProducts());
        assertEquals(12L, response.getTotalOrders());
        assertEquals(5L, response.getActiveOrders());
        assertEquals(
                new BigDecimal("950.00"),
                response.getTotalRevenue()
        );
    }

    @Test
    void platformStatisticsShouldMapDetailedMetrics() {
        when(productRepository
                .countByStatusAndStockLessThanEqual(
                        ProductStatus.ACTIVE,
                        0
                ))
                .thenReturn(2L);

        when(paymentRepository
                .sumRevenueByStatusAndPaidAtBetween(
                        eq(PaymentStatus.SUCCESS),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)
                ))
                .thenReturn(
                        new BigDecimal("120.50")
                );

        when(orderRepository
                .countDistinctCustomersExcludingStatus(
                        OrderStatus.CANCELLED
                ))
                .thenReturn(3L);

        when(reviewRepository.findAverageRating())
                .thenReturn(4.25);

        when(paymentRepository
                .findPaymentMethodUsageByStatus(
                        PaymentStatus.SUCCESS
                ))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[]{
                                        PaymentMethod.CASH_ON_DELIVERY,
                                        5L
                                }
                        )
                );

        when(orderItemRepository
                .findBestSellingProducts(
                        eq(OrderStatus.DELIVERED),
                        any(Pageable.class)
                ))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[]{
                                        "Produkti Test",
                                        4L
                                }
                        )
                );

        when(orderItemRepository
                .findTopSellingCategories(
                        eq(OrderStatus.DELIVERED),
                        any(Pageable.class)
                ))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[]{
                                        "Elektronikë",
                                        4L
                                }
                        )
                );

        OrganizationStatisticsResponse response =
                statisticsService
                        .getPlatformStatistics();

        assertEquals(
                2L,
                response.getOutOfStockProducts()
        );

        assertEquals(
                new BigDecimal("120.50"),
                response.getSalesToday()
        );

        assertEquals(
                3L,
                response.getActiveCustomers()
        );

        assertEquals(
                new BigDecimal("4.3"),
                response.getAverageRating()
        );

        assertEquals(
                "CASH_ON_DELIVERY",
                response.getMostUsedPaymentMethod()
        );

        assertEquals(
                "Elektronikë",
                response.getTopSellingCategory()
        );

        assertEquals(
                "Produkti Test",
                response.getBestSellingProduct()
        );
    }
    @Test
    void unauthorizedMemberCannotReadStatistics() {
        doThrow(
                new AccessDeniedException("Denied")
        )
                .when(organizationAccessService)
                .requireCanAccessOrganization(
                        ORGANIZATION_ID
                );

        assertThrows(
                AccessDeniedException.class,
                () -> statisticsService
                        .getOrganizationStatistics(
                                ORGANIZATION_ID
                        )
        );

        verifyNoInteractions(
                productRepository,
                orderRepository,
                paymentRepository,
                organizationRepository,
                orderItemRepository,
                reviewRepository
        );
    }

    @Test
    void nullRevenueShouldReturnZero() {
        when(organizationRepository.existsById(
                ORGANIZATION_ID
        )).thenReturn(true);

        when(paymentRepository
                .sumRevenueByOrganizationIdAndStatus(
                        ORGANIZATION_ID,
                        PaymentStatus.SUCCESS
                ))
                .thenReturn(null);

        OrganizationStatisticsResponse response =
                statisticsService
                        .getOrganizationStatistics(
                                ORGANIZATION_ID
                        );

        assertEquals(
                BigDecimal.ZERO,
                response.getTotalRevenue()
        );
    }
}