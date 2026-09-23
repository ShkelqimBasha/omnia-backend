package com.omnia.backend.service.impl;

import com.omnia.backend.dto.response.OrganizationStatisticsResponse;
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.enums.PaymentStatus;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.repository.OrderRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.PaymentRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class StatisticsService {

    private static final int LOW_STOCK_LIMIT = 5;

    private static final List<OrderStatus>
            FINISHED_ORDER_STATUSES =
            List.of(
                    OrderStatus.DELIVERED,
                    OrderStatus.CANCELLED
            );

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAccessService
            organizationAccessService;

    public StatisticsService(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            OrganizationRepository organizationRepository,
            OrganizationAccessService
                    organizationAccessService
    ) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.organizationRepository =
                organizationRepository;
        this.organizationAccessService =
                organizationAccessService;
    }

    public OrganizationStatisticsResponse
    getOrganizationStatistics(
            Long organizationId
    ) {
        organizationAccessService
                .requireCanAccessOrganization(
                        organizationId
                );

        if (!organizationRepository.existsById(
                organizationId
        )) {
            throw new ResourceNotFoundException(
                    "Organization not found"
            );
        }

        BigDecimal totalRevenue =
                paymentRepository
                        .sumRevenueByOrganizationIdAndStatus(
                                organizationId,
                                PaymentStatus.SUCCESS
                        );

        return OrganizationStatisticsResponse.builder()
                .totalProducts(
                        productRepository
                                .countByOrganizationId(
                                        organizationId
                                )
                )
                .activeProducts(
                        productRepository
                                .countByOrganizationIdAndStatus(
                                        organizationId,
                                        ProductStatus.ACTIVE
                                )
                )
                .lowStockProducts(
                        productRepository
                                .countByOrganizationIdAndStatusAndStockLessThan(
                                        organizationId,
                                        ProductStatus.ACTIVE,
                                        LOW_STOCK_LIMIT
                                )
                )
                .totalOrders(
                        orderRepository
                                .countByOrganizationId(
                                        organizationId
                                )
                )
                .activeOrders(
                        orderRepository
                                .countByOrganizationIdAndStatusNotIn(
                                        organizationId,
                                        FINISHED_ORDER_STATUSES
                                )
                )
                .totalRevenue(
                        zeroIfNull(totalRevenue)
                )
                .build();
    }

    public OrganizationStatisticsResponse
    getPlatformStatistics() {

        BigDecimal totalRevenue =
                paymentRepository.sumRevenueByStatus(
                        PaymentStatus.SUCCESS
                );

        return OrganizationStatisticsResponse.builder()
                .totalProducts(
                        productRepository.count()
                )
                .activeProducts(
                        productRepository.countByStatus(
                                ProductStatus.ACTIVE
                        )
                )
                .lowStockProducts(
                        productRepository
                                .countByStatusAndStockLessThan(
                                        ProductStatus.ACTIVE,
                                        LOW_STOCK_LIMIT
                                )
                )
                .totalOrders(
                        orderRepository.count()
                )
                .activeOrders(
                        orderRepository.countByStatusNotIn(
                                FINISHED_ORDER_STATUSES
                        )
                )
                .totalRevenue(
                        zeroIfNull(totalRevenue)
                )
                .build();
    }

    private BigDecimal zeroIfNull(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }
}