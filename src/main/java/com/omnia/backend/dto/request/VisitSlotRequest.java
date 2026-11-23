package com.omnia.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VisitSlotRequest {
    /** Local time in Europe/Tirane, e.g. 2026-12-01T10:30. */
    @NotNull
    private LocalDateTime startsAt;
}
