package com.omnia.backend.dto.request;

import com.omnia.backend.enums.InquiryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InquiryStatusUpdateRequest {
    @NotNull
    private InquiryStatus status;
}
