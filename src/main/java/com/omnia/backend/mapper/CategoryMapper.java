package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.CategoryResponse;
import com.omnia.backend.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    private com.omnia.backend.catalog.CatalogSettings catalogSettings;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCatalogSettings(com.omnia.backend.catalog.CatalogSettings catalogSettings) {
        this.catalogSettings = catalogSettings;
    }

    public CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .parentName(category.getParent() != null ? category.getParent().getName() : null)
                .name(category.getName())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .status(category.getStatus().name())
                .kind(kind(category).name())
                .sortOrder(category.getSortOrder())
                .fields(com.omnia.backend.catalog.CategoryFieldCatalog.fieldsFor(kind(category)))
                .cartEnabled(kind(category).usesCart())
                .contactEnabled(kind(category).allowsContact())
                .visitRequestEnabled(kind(category).allowsVisitRequest())
                .onlinePurchaseOption(kind(category) == com.omnia.backend.enums.CategoryKind.CAR
                        && com.omnia.backend.catalog.CatalogSettings.carOnline(catalogSettings))
                .build();
    }

    private static com.omnia.backend.enums.CategoryKind kind(Category category) {
        return com.omnia.backend.enums.CategoryKind.fromNullable(category.getKind());
    }
}