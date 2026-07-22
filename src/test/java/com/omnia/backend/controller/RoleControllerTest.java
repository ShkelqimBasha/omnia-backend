package com.omnia.backend.controller;

import com.omnia.backend.entity.Role;
import com.omnia.backend.service.interfaces.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    private RoleService roleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        RoleController controller =
                new RoleController(roleService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void getRoles_ShouldReturnAllRoles()
            throws Exception {

        Role userRole = Role.builder()
                .id(1L)
                .name("USER")
                .description("Standard application user")
                .createdAt(
                        LocalDateTime.of(
                                2026,
                                7,
                                22,
                                12,
                                0
                        )
                )
                .build();

        Role adminRole = Role.builder()
                .id(2L)
                .name("ADMIN")
                .description("Application administrator")
                .createdAt(
                        LocalDateTime.of(
                                2026,
                                7,
                                22,
                                12,
                                5
                        )
                )
                .build();

        when(roleService.getAllRoles())
                .thenReturn(
                        List.of(
                                userRole,
                                adminRole
                        )
                );

        mockMvc.perform(
                        get("/api/roles")
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$").isArray())
                .andExpect(
                        jsonPath("$.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$[0].id")
                                .value(1L)
                )
                .andExpect(
                        jsonPath("$[0].name")
                                .value("USER")
                )
                .andExpect(
                        jsonPath("$[0].description")
                                .value("Standard application user")
                )
                .andExpect(
                        jsonPath("$[0].createdAt")
                                .isNotEmpty()
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(2L)
                )
                .andExpect(
                        jsonPath("$[1].name")
                                .value("ADMIN")
                )
                .andExpect(
                        jsonPath("$[1].description")
                                .value("Application administrator")
                );

        verify(roleService).getAllRoles();
        verifyNoMoreInteractions(roleService);
    }
}