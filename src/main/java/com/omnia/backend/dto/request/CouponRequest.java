package com.omnia.backend.dto.request;

import com.omnia.backend.enums.DiscountType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponRequest {

    private String code;

    private DiscountType discountType;

    private BigDecimal discountValue;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Integer usageLimit;
}