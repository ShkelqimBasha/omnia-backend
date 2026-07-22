package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.CategoryResponse;
import com.omnia.backend.entity.Category;
import com.omnia.backend.enums.CategoryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryMapperTest {

    private CategoryMapper categoryMapper;

    @BeforeEach
    void setUp() {
        categoryMapper = new CategoryMapper();
    }

    @Test
    void toResponse_WithParent_ShouldMapAllFields() {

        Category parent = Category.builder()
                .id(5L)
                .name("Main Category")
                .status(CategoryStatus.ACTIVE)
                .build();

        Category category = Category.builder()
                .id(10L)
                .parent(parent)
                .name("Electronics")
                .description("Electronic products")
                .imageUrl("/images/electronics.png")
                .status(CategoryStatus.ACTIVE)
                .build();

        CategoryResponse response =
                categoryMapper.toResponse(category);

        assertThat(response)
                .isNotNull();

        assertThat(response.getId())
                .isEqualTo(10L);

        assertThat(response.getParentId())
                .isEqualTo(5L);

        assertThat(response.getParentName())
                .isEqualTo("Main Category");

        assertThat(response.getName())
                .isEqualTo("Electronics");

        assertThat(response.getDescription())
                .isEqualTo("Electronic products");

        assertThat(response.getImageUrl())
                .isEqualTo("/images/electronics.png");

        assertThat(response.getStatus())
                .isEqualTo("ACTIVE");
    }

    @Test
    void toResponse_WithoutParent_ShouldMapNullParentFields() {

        Category category = Category.builder()
                .id(11L)
                .parent(null)
                .name("Books")
                .description("Books and publications")
                .imageUrl(null)
                .status(CategoryStatus.INACTIVE)
                .build();

        CategoryResponse response =
                categoryMapper.toResponse(category);

        assertThat(response)
                .isNotNull();

        assertThat(response.getId())
                .isEqualTo(11L);

        assertThat(response.getParentId())
                .isNull();

        assertThat(response.getParentName())
                .isNull();

        assertThat(response.getName())
                .isEqualTo("Books");

        assertThat(response.getDescription())
                .isEqualTo("Books and publications");

        assertThat(response.getImageUrl())
                .isNull();

        assertThat(response.getStatus())
                .isEqualTo("INACTIVE");
    }
}