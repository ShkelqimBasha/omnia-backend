package com.omnia.backend.integration;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.contains;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import com.omnia.backend.dto.request.ProductRequest;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Product;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.ProductRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));
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
    @Test
    void getAllProducts_WithKeyword_ShouldSearchCaseInsensitivelyAcrossFields()
            throws Exception {

        createProduct(
                "Galaxy Phone",
                "Premium smartphone",
                "Samsung",
                new BigDecimal("899.99"),
                null,
                10
        );

        createProduct(
                "Android Tablet",
                "Perfect Galaxy companion",
                "Samsung",
                new BigDecimal("499.99"),
                null,
                8
        );

        createProduct(
                "Smart Watch",
                "Fitness wearable",
                "Galaxy",
                new BigDecimal("299.99"),
                null,
                15
        );

        createProduct(
                "Laptop Pro",
                "Professional computer",
                "Omnia",
                new BigDecimal("1299.99"),
                null,
                5
        );

        mockMvc.perform(
                        get("/api/products")
                                .param("keyword", "  GaLaXy  ")
                                .param("sortBy", "id")
                                .param("sortDir", "asc")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].name").value(
                        containsInAnyOrder(
                                "Galaxy Phone",
                                "Android Tablet",
                                "Smart Watch"
                        )
                ));
    }

    @Test
    void getAllProducts_WithSqlWildcardInKeyword_ShouldTreatItAsLiteral()
            throws Exception {

        createProduct(
                "Model A_1",
                "Product containing an underscore",
                "Omnia",
                new BigDecimal("100.00"),
                null,
                5
        );

        createProduct(
                "Model AB1",
                "Product without an underscore",
                "Omnia",
                new BigDecimal("120.00"),
                null,
                5
        );

        mockMvc.perform(
                        get("/api/products")
                                .param("keyword", "A_1")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value(
                        "Model A_1"
                ));
    }

    @Test
    void getAllProducts_WithCategoryId_ShouldReturnOnlyProductsFromCategory()
            throws Exception {

        Category computers = Category.builder()
                .name("Computers")
                .description("Computers and accessories")
                .imageUrl("https://example.com/computers.jpg")
                .status(CategoryStatus.ACTIVE)
                .build();

        computers = categoryRepository.save(computers);

        createProduct(
                "Smartphone",
                "Premium smartphone",
                "Samsung",
                new BigDecimal("899.99"),
                null,
                10
        );

        Product laptop = Product.builder()
                .name("Laptop Pro")
                .description("Professional laptop")
                .brand("Omnia")
                .price(new BigDecimal("1299.99"))
                .discountPrice(null)
                .stock(5)
                .category(computers)
                .status(ProductStatus.ACTIVE)
                .build();

        productRepository.save(laptop);

        mockMvc.perform(
                        get("/api/products")
                                .param(
                                        "categoryId",
                                        computers.getId().toString()
                                )
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value(
                        "Laptop Pro"
                ))
                .andExpect(jsonPath("$.content[0].category").value(
                        "Computers"
                ));
    }
    @Test
    void getAllProducts_WithBrand_ShouldReturnOnlyExactBrandIgnoringCaseAndSpaces()
            throws Exception {

        createProduct(
                "Galaxy Phone",
                "Premium smartphone",
                "Samsung",
                new BigDecimal("899.99"),
                null,
                10
        );

        createProduct(
                "Galaxy Tablet",
                "Premium tablet",
                "Samsung",
                new BigDecimal("599.99"),
                null,
                8
        );

        createProduct(
                "Smart TV",
                "Premium television",
                "Samsung Electronics",
                new BigDecimal("999.99"),
                null,
                4
        );

        createProduct(
                "iPhone",
                "Apple smartphone",
                "Apple",
                new BigDecimal("1199.99"),
                null,
                12
        );

        mockMvc.perform(
                        get("/api/products")
                                .param("brand", "  sAmSuNg  ")
                                .param("sortBy", "name")
                                .param("sortDir", "asc")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].name").value(
                        containsInAnyOrder(
                                "Galaxy Phone",
                                "Galaxy Tablet"
                        )
                ));
    }
    @Test
    void getAllProducts_WithStatus_ShouldReturnOnlyProductsWithRequestedStatus()
            throws Exception {

        createProduct(
                "Active Product",
                "Currently available product",
                "Omnia",
                new BigDecimal("199.99"),
                null,
                10
        );

        Product inactiveProduct = createProduct(
                "Inactive Product",
                "Currently unavailable product",
                "Omnia",
                new BigDecimal("299.99"),
                null,
                0
        );

        inactiveProduct.setStatus(ProductStatus.INACTIVE);
        productRepository.save(inactiveProduct);

        mockMvc.perform(
                        get("/api/products")
                                .param("status", "INACTIVE")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value(
                        "Inactive Product"
                ))
                .andExpect(jsonPath("$.content[0].status").value(
                        "INACTIVE"
                ));
    }
    @Test
    void getAllProducts_WithPriceRange_ShouldReturnProductsWithinInclusiveRange()
            throws Exception {

        createProduct(
                "Budget Product",
                "Below range",
                "Omnia",
                new BigDecimal("49.99"),
                null,
                10
        );

        createProduct(
                "Minimum Boundary Product",
                "At minimum price",
                "Omnia",
                new BigDecimal("100.00"),
                null,
                10
        );

        createProduct(
                "Maximum Boundary Product",
                "At maximum price",
                "Omnia",
                new BigDecimal("200.00"),
                null,
                10
        );

        createProduct(
                "Premium Product",
                "Above range",
                "Omnia",
                new BigDecimal("250.00"),
                null,
                10
        );

        mockMvc.perform(
                        get("/api/products")
                                .param("minPrice", "100.00")
                                .param("maxPrice", "200.00")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].name").value(
                        containsInAnyOrder(
                                "Minimum Boundary Product",
                                "Maximum Boundary Product"
                        )
                ));
    }

    @Test
    void getAllProducts_WithMinimumPrice_ShouldReturnProductsAtOrAboveMinimum()
            throws Exception {

        createProduct(
                "Cheap Product",
                "Below minimum",
                "Omnia",
                new BigDecimal("99.99"),
                null,
                10
        );

        createProduct(
                "Eligible Product",
                "At minimum",
                "Omnia",
                new BigDecimal("100.00"),
                null,
                10
        );

        mockMvc.perform(
                        get("/api/products")
                                .param("minPrice", "100.00")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value(
                        "Eligible Product"
                ));
    }

    @Test
    void getAllProducts_WithMaximumPrice_ShouldReturnProductsAtOrBelowMaximum()
            throws Exception {

        createProduct(
                "Eligible Product",
                "At maximum",
                "Omnia",
                new BigDecimal("200.00"),
                null,
                10
        );

        createProduct(
                "Expensive Product",
                "Above maximum",
                "Omnia",
                new BigDecimal("200.01"),
                null,
                10
        );

        mockMvc.perform(
                        get("/api/products")
                                .param("maxPrice", "200.00")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value(
                        "Eligible Product"
                ));
    }
    @Test
    void getAllProducts_WithNegativePage_ShouldReturnBadRequest()
            throws Exception {

        mockMvc.perform(
                        get("/api/products")
                                .param("page", "-1")
                                .param("size", "10")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(
                        "Page number must not be negative"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/api/products"
                ));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void getAllProducts_WithInvalidSize_ShouldReturnBadRequest(
            int invalidSize
    ) throws Exception {

        mockMvc.perform(
                        get("/api/products")
                                .param("page", "0")
                                .param(
                                        "size",
                                        Integer.toString(invalidSize)
                                )
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(
                        "Page size must be between 1 and 100"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/api/products"
                ));
    }
    @ParameterizedTest
    @CsvSource({
            "-0.01, 100.00, Minimum price must not be negative",
            "0.00, -0.01, Maximum price must not be negative",
            "200.00, 100.00, Minimum price must not be greater than maximum price"
    })
    void getAllProducts_WithInvalidPriceRange_ShouldReturnBadRequest(
            String minPrice,
            String maxPrice,
            String expectedMessage
    ) throws Exception {

        mockMvc.perform(
                        get("/api/products")
                                .param("minPrice", minPrice)
                                .param("maxPrice", maxPrice)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(
                        expectedMessage
                ))
                .andExpect(jsonPath("$.path").value(
                        "/api/products"
                ));
    }


    @Test
    void getAllProducts_SortedByPriceDescending_ShouldReturnCorrectOrder()
            throws Exception {

        createProduct(
                "Budget Product",
                "Lowest price",
                "Omnia",
                new BigDecimal("49.99"),
                null,
                10
        );

        createProduct(
                "Premium Product",
                "Highest price",
                "Omnia",
                new BigDecimal("999.99"),
                null,
                10
        );

        createProduct(
                "Standard Product",
                "Middle price",
                "Omnia",
                new BigDecimal("299.99"),
                null,
                10
        );

        mockMvc.perform(
                        get("/api/products")
                                .param("sortBy", "PRICE")
                                .param("sortDir", "  DeSc  ")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(
                        contains(
                                "Premium Product",
                                "Standard Product",
                                "Budget Product"
                        )
                ));
    }

    @Test
    void getAllProducts_SortedByNameAscending_ShouldReturnCorrectOrder()
            throws Exception {

        createProduct(
                "Gamma Product",
                "Third alphabetically",
                "Omnia",
                new BigDecimal("100.00"),
                null,
                10
        );

        createProduct(
                "Alpha Product",
                "First alphabetically",
                "Omnia",
                new BigDecimal("100.00"),
                null,
                10
        );

        createProduct(
                "Beta Product",
                "Second alphabetically",
                "Omnia",
                new BigDecimal("100.00"),
                null,
                10
        );

        mockMvc.perform(
                        get("/api/products")
                                .param("sortBy", "name")
                                .param("sortDir", "asc")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(
                        contains(
                                "Alpha Product",
                                "Beta Product",
                                "Gamma Product"
                        )
                ));
    }

    @Test
    void getAllProducts_SortedByCreatedAtDescending_ShouldReturnNewestFirst()
            throws Exception {

        Product oldestProduct = createProduct(
                "Oldest Product",
                "Created first",
                "Omnia",
                new BigDecimal("100.00"),
                null,
                10
        );

        Product newestProduct = createProduct(
                "Newest Product",
                "Created last",
                "Omnia",
                new BigDecimal("100.00"),
                null,
                10
        );

        jdbcTemplate.update(
                "UPDATE products SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(
                        LocalDateTime.of(2026, 1, 1, 10, 0)
                ),
                oldestProduct.getId()
        );

        jdbcTemplate.update(
                "UPDATE products SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(
                        LocalDateTime.of(2026, 2, 1, 10, 0)
                ),
                newestProduct.getId()
        );

        mockMvc.perform(
                        get("/api/products")
                                .param("sortBy", "createdAt")
                                .param("sortDir", "desc")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(
                        contains(
                                "Newest Product",
                                "Oldest Product"
                        )
                ));
    }

    @Test
    void getAllProducts_WithUnsupportedSortField_ShouldReturnBadRequest()
            throws Exception {

        mockMvc.perform(
                        get("/api/products")
                                .param("sortBy", "unknownField")
                                .param("sortDir", "asc")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(
                        "Unsupported sort field. Allowed values: "
                                + "id, name, brand, price, discountPrice, "
                                + "stock, status, createdAt, updatedAt"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/api/products"
                ));
    }

    @Test
    void getAllProducts_WithUnsupportedSortDirection_ShouldReturnBadRequest()
            throws Exception {

        mockMvc.perform(
                        get("/api/products")
                                .param("sortBy", "price")
                                .param("sortDir", "sideways")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(
                        "Sort direction must be either asc or desc"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/api/products"
                ));
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