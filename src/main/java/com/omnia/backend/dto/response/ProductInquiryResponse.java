package com.omnia.backend.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductInquiryResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Long organizationId;
    private String type;
    private String name;
    private String phone;
    private String message;
    private LocalDate preferredDate;
    private String status;
    private LocalDateTime createdAt;
}
