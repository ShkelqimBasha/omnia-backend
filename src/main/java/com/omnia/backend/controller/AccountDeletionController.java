package com.omnia.backend.controller;

import com.omnia.backend.dto.request.DeleteAccountRequest;
import com.omnia.backend.service.impl.AccountDeletionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@PreAuthorize("isAuthenticated()")
public class AccountDeletionController {

    private final AccountDeletionService accountDeletionService;

    public AccountDeletionController(AccountDeletionService accountDeletionService) {
        this.accountDeletionService = accountDeletionService;
    }

    /** Deletes (soft-deletes and anonymises) the signed-in user's own account. */
    @DeleteMapping
    public ResponseEntity<Void> deleteMyAccount(
            @Valid @RequestBody(required = false) DeleteAccountRequest request
    ) {
        accountDeletionService.deleteCurrentAccount(request);
        return ResponseEntity.noContent().build();
    }
}
