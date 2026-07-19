package com.omnia.backend.integration;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import com.omnia.backend.dto.request.ProductRequest;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Product;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockUser(
        username = "integration-user@example.com",
        roles = "USER"
)
class ProductControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category category;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        category = Category.builder()
                .name("Electronics")
                .description("Electronic devices")
                .imageUrl("https://example.com/electronics.jpg")
                .status(CategoryStatus.ACTIVE)
                .build();

        category = categoryRepository.save(category);
    }

    @Test
    void getProductById_WhenProductExists_ShouldReturnProduct()
            throws Exception {

        Product product = createProduct(
                "Samsung Galaxy S24",
                "Premium Android smartphone",
                "Samsung",
                new BigDecimal("899.99"),
                new BigDecimal("849.99"),
                15
        );

        mockMvc.perform(
                        get("/api/products/{id}", product.getId())
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.name").value(
                        "Samsung Galaxy S24"
                ))
                .andExpect(jsonPath("$.description").value(
                        "Premium Android smartphone"
                ))
                .andExpect(jsonPath("$.brand").value("Samsung"))
                .andExpect(jsonPath("$.price").value(899.99))
                .andExpect(jsonPath("$.discountPrice").value(849.99))
                .andExpect(jsonPath("$.stock").value(15))
                .andExpect(jsonPath("$.category").value(
                        "Electronics"
                ))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getAllProducts_ShouldReturnPaginatedProducts()
            throws Exception {

        createProduct(
                "Laptop Pro",
                "Professional laptop",
                "Omnia",
                new BigDecimal("1299.99"),
                null,
                8
        );

        createProduct(
                "Wireless Mouse",
                "Ergonomic wireless mouse",
                "Omnia",
                new BigDecimal("49.99"),
                new BigDecimal("39.99"),
                30
        );

        mockMvc.perform(
                        get("/api/products")
                                .param("page", "0")
                                .param("size", "10")
                                .param("sortBy", "id")
                                .param("sortDir", "asc")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void getProductById_WhenProductDoesNotExist_ShouldReturnNotFound()
            throws Exception {

        long nonExistingId = 999999L;

        mockMvc.perform(
                        get("/api/products/{id}", nonExistingId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(
                        "Product not found"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/api/products/" + nonExistingId
                ));
    }

    @Test
    @WithMockUser(
            username = "admin@example.com",
            roles = "ADMIN"
    )
    void createProduct_AsAdmin_ShouldReturnCreatedAndSaveProduct()
            throws Exception {

        ProductRequest request = ProductRequest.builder()
                .name("iPhone 16 Pro")
                .description("Premium Apple smartphone")
                .price(new BigDecimal("1199.99"))
                .discountPrice(new BigDecimal("1099.99"))
                .stock(20)
                .categoryId(category.getId())
                .brand("Apple")
                .build();

        mockMvc.perform(
                        post("/api/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(
                        "iPhone 16 Pro"
                ))
                .andExpect(jsonPath("$.description").value(
                        "Premium Apple smartphone"
                ))
                .andExpect(jsonPath("$.price").value(1199.99))
                .andExpect(jsonPath("$.discountPrice").value(1099.99))
                .andExpect(jsonPath("$.stock").value(20))
                .andExpect(jsonPath("$.brand").value("Apple"))
                .andExpect(jsonPath("$.category").value(
                        "Electronics"
                ))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(productRepository.count()).isEqualTo(1);

        Product savedProduct = productRepository.findAll()
                .stream()
                .findFirst()
                .orElseThrow();

        assertThat(savedProduct.getName())
                .isEqualTo("iPhone 16 Pro");

        assertThat(savedProduct.getDescription())
                .isEqualTo("Premium Apple smartphone");

        assertThat(savedProduct.getPrice())
                .isEqualByComparingTo("1199.99");

        assertThat(savedProduct.getDiscountPrice())
                .isEqualByComparingTo("1099.99");

        assertThat(savedProduct.getStock()).isEqualTo(20);
        assertThat(savedProduct.getBrand()).isEqualTo("Apple");

        assertThat(savedProduct.getCategory().getId())
                .isEqualTo(category.getId());

        assertThat(savedProduct.getStatus())
                .isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @WithMockUser(
            username = "regular-user@example.com",
            roles = "USER"
    )
    void createProduct_AsUser_ShouldReturnForbidden() throws Exception {

        ProductRequest request = ProductRequest.builder()
                .name("Forbidden Product")
                .description("Should not be created")
                .price(new BigDecimal("100.00"))
                .discountPrice(null)
                .stock(5)
                .categoryId(category.getId())
                .brand("Test")
                .build();

        mockMvc.perform(
                        post("/api/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andDo(print())
                .andExpect(status().isForbidden());

        assertThat(productRepository.count()).isZero();
    }

    @Test
    @WithMockUser(
            username = "admin@example.com",
            roles = "ADMIN"
    )
    void createProduct_WithInvalidRequest_ShouldReturnBadRequest()
            throws Exception {

        ProductRequest request = ProductRequest.builder()
                .name(" ")
                .description("Invalid product")
                .price(new BigDecimal("-20.00"))
                .discountPrice(null)
                .stock(-1)
                .categoryId(null)
                .brand("Invalid")
                .build();

        mockMvc.perform(
                        post("/api/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value(
                        "/api/products"
                ));

        assertThat(productRepository.count()).isZero();
    }

    @Test
    @WithMockUser(
            username = "admin@example.com",
            roles = "ADMIN"
    )
    void updateProduct_AsAdmin_ShouldUpdateProduct()
            throws Exception {

        Product product = createProduct(
                "Old Product",
                "Old description",
                "Old Brand",
                new BigDecimal("500.00"),
                null,
                5
        );

        Category updatedCategory = Category.builder()
                .name("Computers")
                .description("Computers and accessories")
                .imageUrl("https://example.com/computers.jpg")
                .status(CategoryStatus.ACTIVE)
                .build();

        updatedCategory =
                categoryRepository.save(updatedCategory);

        ProductRequest request = ProductRequest.builder()
                .name("Updated Laptop")
                .description("Updated product description")
                .price(new BigDecimal("1499.99"))
                .discountPrice(new BigDecimal("1399.99"))
                .stock(25)
                .categoryId(updatedCategory.getId())
                .brand("Updated Brand")
                .build();

        mockMvc.perform(
                        put("/api/products/{id}", product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.name").value(
                        "Updated Laptop"
                ))
                .andExpect(jsonPath("$.description").value(
                        "Updated product description"
                ))
                .andExpect(jsonPath("$.price").value(1499.99))
                .andExpect(jsonPath("$.discountPrice").value(1399.99))
                .andExpect(jsonPath("$.stock").value(25))
                .andExpect(jsonPath("$.brand").value(
                        "Updated Brand"
                ))
                .andExpect(jsonPath("$.category").value(
                        "Computers"
                ))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Product updatedProduct = productRepository
                .findById(product.getId())
                .orElseThrow();

        assertThat(updatedProduct.getName())
                .isEqualTo("Updated Laptop");

        assertThat(updatedProduct.getPrice())
                .isEqualByComparingTo("1499.99");

        assertThat(updatedProduct.getDiscountPrice())
                .isEqualByComparingTo("1399.99");

        assertThat(updatedProduct.getStock()).isEqualTo(25);

        assertThat(updatedProduct.getCategory().getId())
                .isEqualTo(updatedCategory.getId());

        assertThat(updatedProduct.getStatus())
                .isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @WithMockUser(
            username = "admin@example.com",
            roles = "ADMIN"
    )
    void deleteProduct_AsAdmin_ShouldSoftDeleteProduct()
            throws Exception {

        Product product = createProduct(
                "Product To Delete",
                "Product for soft-delete testing",
                "Test Brand",
                new BigDecimal("199.99"),
                null,
                10
        );

        mockMvc.perform(
                        delete("/api/products/{id}", product.getId())
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Product softDeletedProduct = productRepository
                .findById(product.getId())
                .orElseThrow();

        assertThat(softDeletedProduct.getStatus())
                .isEqualTo(ProductStatus.INACTIVE);

        assertThat(productRepository.existsById(product.getId()))
                .isTrue();
    }

    private Product createProduct(
            String name,
            String description,
            String brand,
            BigDecimal price,
            BigDecimal discountPrice,
            int stock
    ) {
        Product product = Product.builder()
                .name(name)
                .description(description)
                .brand(brand)
                .price(price)
                .discountPrice(discountPrice)
                .stock(stock)
                .category(category)
                .status(ProductStatus.ACTIVE)
                .build();

        return productRepository.save(product);
    }
}