package com.omnia.backend.service;

import com.omnia.backend.entity.Role;
import com.omnia.backend.repository.RoleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleService {

    private final RoleRepository repository;

    public RoleService(RoleRepository repository) {
        this.repository = repository;
    }

    public List<Role> getAllRoles() {
        return repository.findAll();
    }
}