package com.omnia.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * POST /api/seller-applications. Validated in SellerApplicationRules so every message is in Albanian.
 * sellerType: "BUSINESS" or "PRIVATE"; nipt only for BUSINESS.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerApplicationRequest {
    private String sellerType;
    private String storeName;
    private String phone;
    private String city;
    private String nipt;
    private List<Long> categoryIds;
    private String description;
}
