package com.omnia.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequest {

    private Long parentId;

    @NotBlank
    private String name;

    private String description;

    private String imageUrl;
}