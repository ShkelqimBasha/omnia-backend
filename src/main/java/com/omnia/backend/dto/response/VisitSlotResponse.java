package com.omnia.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VisitSlotResponse {
    private Long id;
    /** Local time in Europe/Tirane. */
    private LocalDateTime startsAt;
    /** Seller view only. */
    private Boolean booked;
    /** Seller view only: the inquiry that booked it and the buyer's name. */
    private Long inquiryId;
    private String bookedBy;
}
