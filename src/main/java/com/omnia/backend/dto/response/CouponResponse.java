package com.omnia.backend.dto.response;

import com.omnia.backend.enums.CouponStatus;
import com.omnia.backend.enums.DiscountType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponResponse {

    private Long id;

    private String code;

    private DiscountType discountType;

    private BigDecimal discountValue;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Integer usageLimit;

    private CouponStatus status;
}