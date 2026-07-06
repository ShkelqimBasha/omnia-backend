package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.CategoryRequest;
import com.omnia.backend.dto.response.CategoryResponse;
import com.omnia.backend.entity.Category;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.mapper.CategoryMapper;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.service.interfaces.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(
            CategoryRepository categoryRepository,
            CategoryMapper categoryMapper
    ) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {

        Category parent = null;

        if (request.getParentId() != null) {
            parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));
        }

        Category category = Category.builder()
                .parent(parent)
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .status(CategoryStatus.ACTIVE)
                .build();

        Category savedCategory = categoryRepository.save(category);

        return categoryMapper.toResponse(savedCategory);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {

        return categoryRepository.findAll()
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        Category parent = null;

        if (request.getParentId() != null) {
            parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));
        }

        category.setParent(parent);
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());

        Category updatedCategory = categoryRepository.save(category);

        return categoryMapper.toResponse(updatedCategory);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        category.setStatus(CategoryStatus.INACTIVE);

        categoryRepository.save(category);
    }
}