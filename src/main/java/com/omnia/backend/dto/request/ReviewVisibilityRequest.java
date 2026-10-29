package com.omnia.backend.dto.request;

@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
public class ReviewVisibilityRequest {
    @jakarta.validation.constraints.NotNull
    private Boolean hidden;
}
