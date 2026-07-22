package com.omnia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnia.backend.dto.request.CategoryRequest;
import com.omnia.backend.dto.response.CategoryResponse;
import com.omnia.backend.service.interfaces.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    private static final Long CATEGORY_ID = 10L;
    private static final Long PARENT_ID = 5L;

    @Mock
    private CategoryService categoryService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        CategoryController controller =
                new CategoryController(categoryService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        objectMapper = new ObjectMapper()
                .findAndRegisterModules();
    }

    @Test
    void createCategory_WithValidRequest_ShouldReturnCreated()
            throws Exception {

        CategoryRequest request = createRequest();

        CategoryResponse response =
                createResponse(CATEGORY_ID, "Electronics");

        when(
                categoryService.createCategory(
                        any(CategoryRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post("/api/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.id").value(CATEGORY_ID))
                .andExpect(jsonPath("$.parentId").value(PARENT_ID))
                .andExpect(
                        jsonPath("$.parentName")
                                .value("Main Category")
                )
                .andExpect(
                        jsonPath("$.name")
                                .value("Electronics")
                )
                .andExpect(
                        jsonPath("$.description")
                                .value("Electronic products")
                )
                .andExpect(
                        jsonPath("$.imageUrl")
                                .value("/images/electronics.png")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE")
                );

        ArgumentCaptor<CategoryRequest> requestCaptor =
                ArgumentCaptor.forClass(CategoryRequest.class);

        verify(categoryService).createCategory(
                requestCaptor.capture()
        );

        assertThat(requestCaptor.getValue().getParentId())
                .isEqualTo(PARENT_ID);

        assertThat(requestCaptor.getValue().getName())
                .isEqualTo("Electronics");

        assertThat(requestCaptor.getValue().getDescription())
                .isEqualTo("Electronic products");

        assertThat(requestCaptor.getValue().getImageUrl())
                .isEqualTo("/images/electronics.png");

        verifyNoMoreInteractions(categoryService);
    }

    @Test
    void createCategory_WithBlankName_ShouldReturnBadRequest()
            throws Exception {

        CategoryRequest request = CategoryRequest.builder()
                .parentId(PARENT_ID)
                .name("   ")
                .description("Invalid category")
                .imageUrl("/images/invalid.png")
                .build();

        mockMvc.perform(
                        post("/api/categories")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                categoryService,
                never()
        ).createCategory(any(CategoryRequest.class));

        verifyNoInteractions(categoryService);
    }

    @Test
    void getAllCategories_ShouldReturnCategories()
            throws Exception {

        CategoryResponse firstCategory =
                createResponse(10L, "Electronics");

        CategoryResponse secondCategory =
                createResponse(11L, "Books");

        secondCategory.setParentId(null);
        secondCategory.setParentName(null);
        secondCategory.setDescription("Books and publications");
        secondCategory.setImageUrl("/images/books.png");

        when(categoryService.getAllCategories())
                .thenReturn(
                        List.of(
                                firstCategory,
                                secondCategory
                        )
                );

        mockMvc.perform(
                        get("/api/categories")
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(
                        jsonPath("$[0].name")
                                .value("Electronics")
                )
                .andExpect(jsonPath("$[1].id").value(11L))
                .andExpect(
                        jsonPath("$[1].name")
                                .value("Books")
                )
                .andExpect(
                        jsonPath("$[1].description")
                                .value("Books and publications")
                );

        verify(categoryService).getAllCategories();
        verifyNoMoreInteractions(categoryService);
    }

    @Test
    void getCategoryById_ShouldReturnCategory()
            throws Exception {

        CategoryResponse response =
                createResponse(CATEGORY_ID, "Electronics");

        when(
                categoryService.getCategoryById(CATEGORY_ID)
        ).thenReturn(response);

        mockMvc.perform(
                        get(
                                "/api/categories/{id}",
                                CATEGORY_ID
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.id").value(CATEGORY_ID))
                .andExpect(
                        jsonPath("$.name")
                                .value("Electronics")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE")
                );

        verify(categoryService)
                .getCategoryById(CATEGORY_ID);

        verifyNoMoreInteractions(categoryService);
    }

    @Test
    void updateCategory_WithValidRequest_ShouldReturnUpdatedCategory()
            throws Exception {

        CategoryRequest request = CategoryRequest.builder()
                .parentId(PARENT_ID)
                .name("Updated Electronics")
                .description("Updated description")
                .imageUrl("/images/updated-electronics.png")
                .build();

        CategoryResponse response =
                createResponse(
                        CATEGORY_ID,
                        "Updated Electronics"
                );

        response.setDescription("Updated description");
        response.setImageUrl(
                "/images/updated-electronics.png"
        );

        when(
                categoryService.updateCategory(
                        eq(CATEGORY_ID),
                        any(CategoryRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        put(
                                "/api/categories/{id}",
                                CATEGORY_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.id").value(CATEGORY_ID))
                .andExpect(
                        jsonPath("$.name")
                                .value("Updated Electronics")
                )
                .andExpect(
                        jsonPath("$.description")
                                .value("Updated description")
                )
                .andExpect(
                        jsonPath("$.imageUrl")
                                .value(
                                        "/images/updated-electronics.png"
                                )
                );

        ArgumentCaptor<CategoryRequest> requestCaptor =
                ArgumentCaptor.forClass(CategoryRequest.class);

        verify(categoryService).updateCategory(
                eq(CATEGORY_ID),
                requestCaptor.capture()
        );

        assertThat(requestCaptor.getValue().getName())
                .isEqualTo("Updated Electronics");

        assertThat(requestCaptor.getValue().getDescription())
                .isEqualTo("Updated description");

        verifyNoMoreInteractions(categoryService);
    }

    @Test
    void updateCategory_WithBlankName_ShouldReturnBadRequest()
            throws Exception {

        CategoryRequest request = CategoryRequest.builder()
                .name("")
                .description("Invalid update")
                .build();

        mockMvc.perform(
                        put(
                                "/api/categories/{id}",
                                CATEGORY_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest());

        verify(
                categoryService,
                never()
        ).updateCategory(
                eq(CATEGORY_ID),
                any(CategoryRequest.class)
        );

        verifyNoInteractions(categoryService);
    }

    @Test
    void deleteCategory_ShouldReturnNoContent()
            throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/categories/{id}",
                                CATEGORY_ID
                        )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(categoryService)
                .deleteCategory(CATEGORY_ID);

        verifyNoMoreInteractions(categoryService);
    }

    private CategoryRequest createRequest() {

        return CategoryRequest.builder()
                .parentId(PARENT_ID)
                .name("Electronics")
                .description("Electronic products")
                .imageUrl("/images/electronics.png")
                .build();
    }

    private CategoryResponse createResponse(
            Long id,
            String name
    ) {

        return CategoryResponse.builder()
                .id(id)
                .parentId(PARENT_ID)
                .parentName("Main Category")
                .name(name)
                .description("Electronic products")
                .imageUrl("/images/electronics.png")
                .status("ACTIVE")
                .build();
    }
}