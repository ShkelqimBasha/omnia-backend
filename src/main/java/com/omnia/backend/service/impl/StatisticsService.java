package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.response.DailyRevenueResponse;
import com.omnia.backend.dto.response.OrganizationStatisticsResponse;
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.enums.PaymentStatus;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.repository.OrderItemRepository;
import com.omnia.backend.repository.OrderRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.PaymentRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.ReviewRepository;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class StatisticsService {

    private static final int LOW_STOCK_LIMIT = 5;
    private static final int DEFAULT_REVENUE_DAYS = 7;

    private static final List<OrderStatus>
            FINISHED_ORDER_STATUSES =
            List.of(
                    OrderStatus.DELIVERED,
                    OrderStatus.CANCELLED
            );

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrganizationRepository
            organizationRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;
    private final OrganizationAccessService
            organizationAccessService;

    public StatisticsService(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            OrganizationRepository organizationRepository,
            OrderItemRepository orderItemRepository,
            ReviewRepository reviewRepository,
            OrganizationAccessService
                    organizationAccessService
    ) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.organizationRepository =
                organizationRepository;
        this.orderItemRepository = orderItemRepository;
        this.reviewRepository = reviewRepository;
        this.organizationAccessService =
                organizationAccessService;
    }

    public OrganizationStatisticsResponse
    getOrganizationStatistics(
            Long organizationId
    ) {
        return getOrganizationStatistics(
                organizationId,
                DEFAULT_REVENUE_DAYS
        );
    }

    public OrganizationStatisticsResponse
    getOrganizationStatistics(
            Long organizationId,
            int days
    ) {
        validateRevenueDays(days);
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

        LocalDateTime startOfToday =
                startOfTodayUtc();

        LocalDateTime startOfTomorrow =
                startOfToday.plusDays(1);

        BigDecimal totalRevenue =
                paymentRepository
                        .sumRevenueByOrganizationIdAndStatus(
                                organizationId,
                                PaymentStatus.SUCCESS
                        );

        BigDecimal salesToday =
                paymentRepository
                        .sumRevenueByOrganizationIdAndStatusAndPaidAtBetween(
                                organizationId,
                                PaymentStatus.SUCCESS,
                                startOfToday,
                                startOfTomorrow
                        );

        List<Object[]> paymentMethods =
                paymentRepository
                        .findPaymentMethodUsageByOrganizationIdAndStatus(
                                organizationId,
                                PaymentStatus.SUCCESS
                        );

        List<Object[]> bestSellingProducts =
                orderItemRepository
                        .findBestSellingProductsByOrganizationId(
                                organizationId,
                                OrderStatus.DELIVERED,
                                PageRequest.of(0, 1)
                        );

        List<Object[]> topSellingCategories =
                orderItemRepository
                        .findTopSellingCategoriesByOrganizationId(
                                organizationId,
                                OrderStatus.DELIVERED,
                                PageRequest.of(0, 1)
                        );

        List<Object[]> dailyRevenueRows =
                paymentRepository
                        .findDailyRevenueByOrganizationIdAndStatusAndPaidAtBetween(
                                organizationId,
                                PaymentStatus.SUCCESS,
                                startOfToday.minusDays(days - 1L),
                                startOfTomorrow
                        );

        List<DailyRevenueResponse> dailyRevenue =
                buildDailyRevenue(
                        dailyRevenueRows,
                        startOfToday.toLocalDate(),
                        days
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
                .outOfStockProducts(
                        productRepository
                                .countByOrganizationIdAndStatusAndStockLessThanEqual(
                                        organizationId,
                                        ProductStatus.ACTIVE,
                                        0
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
                .salesToday(
                        zeroIfNull(salesToday)
                )
                .activeCustomers(
                        orderRepository
                                .countDistinctCustomersByOrganizationIdExcludingStatus(
                                        organizationId,
                                        OrderStatus.CANCELLED
                                )
                )
                .averageRating(
                        ratingOrZero(
                                reviewRepository
                                        .findAverageRatingByOrganizationId(
                                                organizationId
                                        )
                        )
                )
                .mostUsedPaymentMethod(
                        firstLabel(paymentMethods)
                )
                .topSellingCategory(
                        firstLabel(topSellingCategories)
                )
                .bestSellingProduct(
                        firstLabel(bestSellingProducts)
                )
                .dailyRevenue(dailyRevenue)
                .build();
    }

    public OrganizationStatisticsResponse
    getPlatformStatistics() {
        return getPlatformStatistics(
                DEFAULT_REVENUE_DAYS
        );
    }

    public OrganizationStatisticsResponse
    getPlatformStatistics(
            int days
    ) {
        validateRevenueDays(days);

        LocalDateTime startOfToday =
                startOfTodayUtc();

        LocalDateTime startOfTomorrow =
                startOfToday.plusDays(1);

        BigDecimal totalRevenue =
                paymentRepository.sumRevenueByStatus(
                        PaymentStatus.SUCCESS
                );

        BigDecimal salesToday =
                paymentRepository
                        .sumRevenueByStatusAndPaidAtBetween(
                                PaymentStatus.SUCCESS,
                                startOfToday,
                                startOfTomorrow
                        );

        List<Object[]> paymentMethods =
                paymentRepository
                        .findPaymentMethodUsageByStatus(
                                PaymentStatus.SUCCESS
                        );

        List<Object[]> bestSellingProducts =
                orderItemRepository
                        .findBestSellingProducts(
                                OrderStatus.DELIVERED,
                                PageRequest.of(0, 1)
                        );

        List<Object[]> topSellingCategories =
                orderItemRepository
                        .findTopSellingCategories(
                                OrderStatus.DELIVERED,
                                PageRequest.of(0, 1)
                        );

        List<Object[]> dailyRevenueRows =
                paymentRepository
                        .findDailyRevenueByStatusAndPaidAtBetween(
                                PaymentStatus.SUCCESS,
                                startOfToday.minusDays(days - 1L),
                                startOfTomorrow
                        );

        List<DailyRevenueResponse> dailyRevenue =
                buildDailyRevenue(
                        dailyRevenueRows,
                        startOfToday.toLocalDate(),
                        days
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
                .outOfStockProducts(
                        productRepository
                                .countByStatusAndStockLessThanEqual(
                                        ProductStatus.ACTIVE,
                                        0
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
                .salesToday(
                        zeroIfNull(salesToday)
                )
                .activeCustomers(
                        orderRepository
                                .countDistinctCustomersExcludingStatus(
                                        OrderStatus.CANCELLED
                                )
                )
                .averageRating(
                        ratingOrZero(
                                reviewRepository
                                        .findAverageRating()
                        )
                )
                .mostUsedPaymentMethod(
                        firstLabel(paymentMethods)
                )
                .topSellingCategory(
                        firstLabel(topSellingCategories)
                )
                .bestSellingProduct(
                        firstLabel(bestSellingProducts)
                )
                .dailyRevenue(dailyRevenue)
                .build();
    }

    private void validateRevenueDays(
            int days
    ) {
        if (days != 7
                && days != 30
                && days != 90) {
            throw new IllegalArgumentException(
                    "Statistics days must be 7, 30, or 90"
            );
        }
    }

    private List<DailyRevenueResponse> buildDailyRevenue(
            List<Object[]> rows,
            LocalDate today,
            int days
    ) {
        Map<LocalDate, BigDecimal> revenueByDate =
                new HashMap<>();

        if (rows != null) {
            for (Object[] row : rows) {
                if (row == null
                        || row.length < 2
                        || row[0] == null
                        || row[1] == null) {
                    continue;
                }

                LocalDate date =
                        parseDatabaseDate(row[0]);

                if (date == null) {
                    continue;
                }

                BigDecimal revenue;

                if (row[1] instanceof BigDecimal) {
                    revenue = (BigDecimal) row[1];
                } else {
                    revenue = new BigDecimal(
                            String.valueOf(row[1])
                    );
                }

                revenueByDate.put(
                        date,
                        revenue
                );
            }
        }

        List<DailyRevenueResponse> result =
                new ArrayList<>();

        for (int offset = days - 1;
             offset >= 0;
             offset--) {
            LocalDate date =
                    today.minusDays(offset);

            result.add(
                    new DailyRevenueResponse(
                            date.toString(),
                            revenueByDate.getOrDefault(
                                    date,
                                    BigDecimal.ZERO
                            )
                    )
            );
        }

        return result;
    }

    private LocalDate parseDatabaseDate(
            Object value
    ) {
        try {
            if (value instanceof LocalDate) {
                return (LocalDate) value;
            }

            if (value instanceof java.sql.Date) {
                return ((java.sql.Date) value)
                        .toLocalDate();
            }

            String text =
                    String.valueOf(value);

            if (text.length() >= 10) {
                text = text.substring(0, 10);
            }

            return LocalDate.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }
    private LocalDateTime startOfTodayUtc() {
        return LocalDate.now(
                ZoneOffset.UTC
        ).atStartOfDay();
    }

    private BigDecimal zeroIfNull(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }

    private BigDecimal ratingOrZero(
            Double rating
    ) {
        if (rating == null) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(rating)
                .setScale(
                        1,
                        RoundingMode.HALF_UP
                );
    }

    private String firstLabel(
            List<Object[]> rows
    ) {
        if (rows == null
                || rows.isEmpty()
                || rows.get(0) == null
                || rows.get(0).length == 0
                || rows.get(0)[0] == null) {
            return null;
        }

        String value =
                String.valueOf(
                        rows.get(0)[0]
                ).trim();

        return value.isEmpty()
                ? null
                : value;
    }
}