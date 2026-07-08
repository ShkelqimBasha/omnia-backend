package com.omnia.backend.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewRequest {

    private Integer rating;

    private String comment;
}