package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.CategoryRequest;
import com.omnia.backend.dto.response.CategoryResponse;

import java.util.List;

public interface CategoryService {

    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse getCategoryById(Long id);

    List<CategoryResponse> getAllCategories();

    CategoryResponse updateCategory(Long id, CategoryRequest request);

    void deleteCategory(Long id);
}