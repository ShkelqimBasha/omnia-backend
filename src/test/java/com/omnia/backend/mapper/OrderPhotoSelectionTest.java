package com.omnia.backend.mapper;

import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductImage;
import com.omnia.backend.entity.UploadedFile;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OrderPhotoSelectionTest {
    @Test void noProductOrPhotosReturnsNull() {
        assertNull(ProductMapper.primaryImageUrl(null));
        Product product = new Product();
        assertNull(ProductMapper.primaryImageUrl(product));
    }
    @Test void selectsPrimaryPhotoEvenWhenItIsLast() {
        Product product = new Product(); product.setId(7L);
        ProductImage secondary = ProductImage.builder().id(1L).product(product)
                .legacyImageUrl("https://example.com/secondary.jpg").isPrimary(false).build();
        ProductImage primary = ProductImage.builder().id(2L).product(product)
                .legacyImageUrl("https://example.com/primary.jpg").isPrimary(true).build();
        product.setImages(List.of(secondary, primary));
        assertEquals("https://example.com/primary.jpg", ProductMapper.primaryImageUrl(product));
    }
    @Test void uploadedPhotoUsesAuthenticatedFileEndpoint() {
        Product product = new Product(); product.setId(7L);
        UploadedFile file = new UploadedFile(); file.setId(42L);
        product.setImages(List.of(ProductImage.builder().id(9L).product(product)
                .uploadedFile(file).isPrimary(true).build()));
        assertEquals("/api/files/42", ProductMapper.primaryImageUrl(product));
    }
    @Test void fallsBackToOldestAvailablePhotoWithoutPrimary() {
        Product product = new Product(); product.setId(7L);
        product.setImages(List.of(
                ProductImage.builder().id(9L).product(product).legacyImageUrl("https://example.com/new.jpg").build(),
                ProductImage.builder().id(3L).product(product).legacyImageUrl("https://example.com/old.jpg").build(),
                ProductImage.builder().id(1L).product(product).build()));
        assertEquals("https://example.com/old.jpg", ProductMapper.primaryImageUrl(product));
    }
}
