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
    private String organizationName;
    /** CAR, PROPERTY, ... (the listing's category kind). */
    private String categoryKind;
    private String type;
    private String name;
    private String phone;
    private String message;
    private LocalDate preferredDate;
    /** The booked visit slot's local time (Europe/Tirane), if the buyer picked one. */
    private LocalDateTime visitAt;
    /** NEW, CONTACTED, VISIT_SCHEDULED or CLOSED. */
    private String status;
    private LocalDateTime statusUpdatedAt;
    private LocalDateTime createdAt;
}
