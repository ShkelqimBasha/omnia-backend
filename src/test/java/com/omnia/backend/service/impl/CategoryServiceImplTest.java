package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.CategoryRequest;
import com.omnia.backend.dto.response.CategoryResponse;
import com.omnia.backend.entity.Category;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.mapper.CategoryMapper;
import com.omnia.backend.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private CategoryRequest categoryRequest;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private Category parentCategory;
    private Category category;
    private CategoryResponse categoryResponse;

    @BeforeEach
    void setUp() {

        parentCategory = Category.builder()
                .id(1L)
                .name("Electronics")
                .description("Electronic products")
                .imageUrl("electronics.jpg")
                .status(CategoryStatus.ACTIVE)
                .build();

        category = Category.builder()
                .id(2L)
                .parent(parentCategory)
                .name("Smartphones")
                .description("Mobile phones")
                .imageUrl("smartphones.jpg")
                .status(CategoryStatus.ACTIVE)
                .build();

        categoryResponse = CategoryResponse.builder()
                .id(2L)
                .name("Smartphones")
                .description("Mobile phones")
                .imageUrl("smartphones.jpg")
                .status(CategoryStatus.ACTIVE.name())
                .build();
    }

    @Test
    void createCategory_shouldCreateRootCategorySuccessfully() {

        when(categoryRequest.getParentId())
                .thenReturn(null);

        when(categoryRequest.getName())
                .thenReturn("Electronics");

        when(categoryRequest.getDescription())
                .thenReturn("Electronic products");

        when(categoryRequest.getImageUrl())
                .thenReturn("electronics.jpg");

        Category rootCategory = Category.builder()
                .id(1L)
                .name("Electronics")
                .description("Electronic products")
                .imageUrl("electronics.jpg")
                .status(CategoryStatus.ACTIVE)
                .build();

        CategoryResponse response = CategoryResponse.builder()
                .id(1L)
                .name("Electronics")
                .description("Electronic products")
                .imageUrl("electronics.jpg")
                .status(CategoryStatus.ACTIVE.name())
                .build();

        when(categoryRepository.save(any(Category.class)))
                .thenReturn(rootCategory);

        when(categoryMapper.toResponse(rootCategory))
                .thenReturn(response);

        CategoryResponse result =
                categoryService.createCategory(categoryRequest);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Electronics", result.getName());

        ArgumentCaptor<Category> categoryCaptor =
                ArgumentCaptor.forClass(Category.class);

        verify(categoryRepository)
                .save(categoryCaptor.capture());

        Category savedCategory = categoryCaptor.getValue();

        assertNull(savedCategory.getParent());
        assertEquals("Electronics", savedCategory.getName());
        assertEquals(
                "Electronic products",
                savedCategory.getDescription()
        );
        assertEquals(
                "electronics.jpg",
                savedCategory.getImageUrl()
        );
        assertEquals(
                CategoryStatus.ACTIVE,
                savedCategory.getStatus()
        );

        verify(categoryRepository, never())
                .findById(anyLong());
    }

    @Test
    void createCategory_shouldCreateChildCategorySuccessfully() {

        when(categoryRequest.getParentId())
                .thenReturn(1L);

        when(categoryRequest.getName())
                .thenReturn("Smartphones");

        when(categoryRequest.getDescription())
                .thenReturn("Mobile phones");

        when(categoryRequest.getImageUrl())
                .thenReturn("smartphones.jpg");

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(parentCategory));

        when(categoryRepository.save(any(Category.class)))
                .thenReturn(category);

        when(categoryMapper.toResponse(category))
                .thenReturn(categoryResponse);

        CategoryResponse result =
                categoryService.createCategory(categoryRequest);

        assertNotNull(result);
        assertEquals(2L, result.getId());
        assertEquals("Smartphones", result.getName());

        ArgumentCaptor<Category> categoryCaptor =
                ArgumentCaptor.forClass(Category.class);

        verify(categoryRepository)
                .save(categoryCaptor.capture());

        Category savedCategory = categoryCaptor.getValue();

        assertEquals(
                parentCategory,
                savedCategory.getParent()
        );
        assertEquals(
                CategoryStatus.ACTIVE,
                savedCategory.getStatus()
        );

        verify(categoryRepository).findById(1L);
    }

    @Test
    void createCategory_shouldThrowWhenParentDoesNotExist() {

        when(categoryRequest.getParentId())
                .thenReturn(99L);

        when(categoryRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> categoryService.createCategory(
                                categoryRequest
                        )
                );

        assertEquals(
                "Parent category not found",
                exception.getMessage()
        );

        verify(categoryRepository, never())
                .save(any(Category.class));

        verifyNoInteractions(categoryMapper);
    }

    @Test
    void getCategoryById_shouldReturnCategory() {

        when(categoryRepository.findById(2L))
                .thenReturn(Optional.of(category));

        when(categoryMapper.toResponse(category))
                .thenReturn(categoryResponse);

        CategoryResponse result =
                categoryService.getCategoryById(2L);

        assertNotNull(result);
        assertEquals(2L, result.getId());
        assertEquals("Smartphones", result.getName());

        verify(categoryRepository).findById(2L);
        verify(categoryMapper).toResponse(category);
    }

    @Test
    void getCategoryById_shouldThrowWhenCategoryDoesNotExist() {

        when(categoryRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> categoryService.getCategoryById(99L)
                );

        assertEquals(
                "Category not found",
                exception.getMessage()
        );

        verifyNoInteractions(categoryMapper);
    }

    @Test
    void getAllCategories_shouldReturnMappedCategories() {

        Category secondCategory = Category.builder()
                .id(3L)
                .name("Laptops")
                .description("Portable computers")
                .imageUrl("laptops.jpg")
                .status(CategoryStatus.ACTIVE)
                .build();

        CategoryResponse secondResponse =
                CategoryResponse.builder()
                        .id(3L)
                        .name("Laptops")
                        .description("Portable computers")
                        .imageUrl("laptops.jpg")
                        .status(CategoryStatus.ACTIVE.name())
                        .build();

        when(categoryRepository.findAll())
                .thenReturn(List.of(category, secondCategory));

        when(categoryMapper.toResponse(category))
                .thenReturn(categoryResponse);

        when(categoryMapper.toResponse(secondCategory))
                .thenReturn(secondResponse);

        List<CategoryResponse> result =
                categoryService.getAllCategories();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Smartphones", result.get(0).getName());
        assertEquals("Laptops", result.get(1).getName());

        verify(categoryRepository).findAll();
        verify(categoryMapper, times(2))
                .toResponse(any(Category.class));
    }

    @Test
    void updateCategory_shouldUpdateCategorySuccessfully() {

        when(categoryRequest.getParentId())
                .thenReturn(1L);

        when(categoryRequest.getName())
                .thenReturn("Updated Smartphones");

        when(categoryRequest.getDescription())
                .thenReturn("Updated description");

        when(categoryRequest.getImageUrl())
                .thenReturn("updated.jpg");

        when(categoryRepository.findById(2L))
                .thenReturn(Optional.of(category));

        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(parentCategory));

        when(categoryRepository.save(category))
                .thenReturn(category);

        CategoryResponse updatedResponse =
                CategoryResponse.builder()
                        .id(2L)
                        .name("Updated Smartphones")
                        .description("Updated description")
                        .imageUrl("updated.jpg")
                        .status(CategoryStatus.ACTIVE.name())
                        .build();

        when(categoryMapper.toResponse(category))
                .thenReturn(updatedResponse);

        CategoryResponse result =
                categoryService.updateCategory(
                        2L,
                        categoryRequest
                );

        assertNotNull(result);
        assertEquals(
                "Updated Smartphones",
                result.getName()
        );

        assertEquals(
                "Updated Smartphones",
                category.getName()
        );
        assertEquals(
                "Updated description",
                category.getDescription()
        );
        assertEquals(
                "updated.jpg",
                category.getImageUrl()
        );
        assertEquals(
                parentCategory,
                category.getParent()
        );

        verify(categoryRepository).save(category);
    }

    @Test
    void updateCategory_shouldRemoveParentWhenParentIdIsNull() {

        when(categoryRequest.getParentId())
                .thenReturn(null);

        when(categoryRequest.getName())
                .thenReturn("Smartphones");

        when(categoryRequest.getDescription())
                .thenReturn("Mobile phones");

        when(categoryRequest.getImageUrl())
                .thenReturn("smartphones.jpg");

        when(categoryRepository.findById(2L))
                .thenReturn(Optional.of(category));

        when(categoryRepository.save(category))
                .thenReturn(category);

        when(categoryMapper.toResponse(category))
                .thenReturn(categoryResponse);

        categoryService.updateCategory(
                2L,
                categoryRequest
        );

        assertNull(category.getParent());

        verify(categoryRepository, times(1))
                .findById(2L);

        verify(categoryRepository).save(category);
    }

    @Test
    void updateCategory_shouldThrowWhenCategoryDoesNotExist() {

        when(categoryRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> categoryService.updateCategory(
                                99L,
                                categoryRequest
                        )
                );

        assertEquals(
                "Category not found",
                exception.getMessage()
        );

        verify(categoryRepository, never())
                .save(any(Category.class));

        verifyNoInteractions(categoryMapper);
    }

    @Test
    void updateCategory_shouldThrowWhenParentDoesNotExist() {

        when(categoryRequest.getParentId())
                .thenReturn(99L);

        when(categoryRepository.findById(2L))
                .thenReturn(Optional.of(category));

        when(categoryRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> categoryService.updateCategory(
                                2L,
                                categoryRequest
                        )
                );

        assertEquals(
                "Parent category not found",
                exception.getMessage()
        );

        verify(categoryRepository, never())
                .save(any(Category.class));

        verifyNoInteractions(categoryMapper);
    }

    @Test
    void deleteCategory_shouldSetStatusInactive() {

        when(categoryRepository.findById(2L))
                .thenReturn(Optional.of(category));

        categoryService.deleteCategory(2L);

        assertEquals(
                CategoryStatus.INACTIVE,
                category.getStatus()
        );

        verify(categoryRepository).save(category);
    }

    @Test
    void deleteCategory_shouldThrowWhenCategoryDoesNotExist() {

        when(categoryRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> categoryService.deleteCategory(99L)
                );

        assertEquals(
                "Category not found",
                exception.getMessage()
        );

        verify(categoryRepository, never())
                .save(any(Category.class));
    }
}