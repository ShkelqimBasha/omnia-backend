package com.omnia.backend.service.impl;

import com.omnia.backend.dto.response.OrganizationStatisticsResponse;
import com.omnia.backend.enums.PaymentStatus;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.repository.OrderRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.PaymentRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                organizationRepository
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