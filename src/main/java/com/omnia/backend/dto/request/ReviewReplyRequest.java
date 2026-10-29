package com.omnia.backend.dto.request;

@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
public class ReviewReplyRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 2000)
    private String reply;
}
