package com.omnia.backend.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private Long id;

    private Long parentId;

    private String parentName;

    private String name;

    private String description;

    private String imageUrl;

    private String status;
}