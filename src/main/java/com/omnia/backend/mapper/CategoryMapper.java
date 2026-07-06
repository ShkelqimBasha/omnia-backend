package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.CategoryResponse;
import com.omnia.backend.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .parentName(category.getParent() != null ? category.getParent().getName() : null)
                .name(category.getName())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .status(category.getStatus().name())
                .build();
    }
}