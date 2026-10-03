package com.omnia.backend.mapper;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.ProductStatus;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class MediaResponseMappingTest {
    private Product product() {
        return Product.builder().id(1L).name("Test").status(ProductStatus.ACTIVE)
                .category(Category.builder().id(1L).name("Elektronikë").build()).build();
    }
    private ProductImage image(Product product,long id,long fileId,boolean primary) {
        return ProductImage.builder().id(id).product(product).isPrimary(primary)
                .uploadedFile(UploadedFile.builder().id(fileId).build()).build();
    }
    @Test void prefersPrimaryImageEvenWhenItsIdIsLarger() {
        Product product = product();
        product.setImages(List.of(image(product,1,10,false),image(product,2,11,true)));
        assertEquals("/api/files/11",new ProductMapper().toResponse(product).getImageUrl());
    }
    @Test void fallsBackToFirstImageAndSupportsMissingImages() {
        Product product = product();
        assertNull(new ProductMapper().toResponse(product).getImageUrl());
        product.setImages(List.of(image(product,3,13,false),image(product,2,12,false)));
        assertEquals("/api/files/12",new ProductMapper().toResponse(product).getImageUrl());
    }
    @Test void exposesCompanyLogoInProductAndCompanyCatalog() {
        Product product = product();
        Organization organization = Organization.builder().id(2L).name("Omnia")
                .logoFile(UploadedFile.builder().id(20L).build()).build();
        product.setOrganization(organization);
        assertEquals("/api/files/20",new ProductMapper().toResponse(product).getOrganizationLogoUrl());
        assertEquals("/api/files/20",new OrganizationMapper().toCatalogResponse(organization).getLogoUrl());
    }
}
