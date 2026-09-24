package com.omnia.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationStatisticsResponse {

    private Long totalProducts;

    private Long activeProducts;

    private Long lowStockProducts;

    private Long totalOrders;

    private Long activeOrders;

    private BigDecimal totalRevenue;

    private Long outOfStockProducts;

    private BigDecimal salesToday;

    private Long activeCustomers;

    private BigDecimal averageRating;

    private String mostUsedPaymentMethod;

    private String topSellingCategory;

    private String bestSellingProduct;
    private List<DailyRevenueResponse> dailyRevenue;}