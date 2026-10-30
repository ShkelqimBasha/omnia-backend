package com.omnia.backend.controller;
import com.omnia.backend.dto.request.*;
import com.omnia.backend.dto.response.UserSettingsResponse;
import com.omnia.backend.service.impl.AccountSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/users/me") @PreAuthorize("isAuthenticated()")
public class AccountSettingsController {
    private final AccountSettingsService service;
    public AccountSettingsController(AccountSettingsService service){this.service=service;}
    @GetMapping("/settings") public UserSettingsResponse get(){return service.get();}
    @PutMapping("/settings") public UserSettingsResponse save(@Valid @RequestBody UserSettingsRequest request){return service.save(request);}
    @PostMapping("/password") public ResponseEntity<Void> password(@Valid @RequestBody ChangeCurrentPasswordRequest request){service.password(request);return ResponseEntity.noContent().build();}
}
