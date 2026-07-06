package com.omnia.backend.controller;

import com.omnia.backend.entity.Role;
import com.omnia.backend.service.interfaces.RoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping
    public List<Role> getRoles() {
        return service.getAllRoles();
    }
}