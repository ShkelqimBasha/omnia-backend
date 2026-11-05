package com.omnia.backend.controller;

import com.omnia.backend.dto.response.AuthResponse;
import com.omnia.backend.service.impl.GoogleAuthService34;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class GoogleAuthController34 {
    private final GoogleAuthService34 service;
    public GoogleAuthController34(GoogleAuthService34 service) { this.service=service; }
    public record Request(@NotBlank @Size(max=16000) String idToken, boolean privacyAccepted) {}
    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody Request request) {
        try {
            return service.authenticate(request.idToken(), request.privacyAccepted());
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            // A concurrent registration must not expose SQL or create duplicate users.
            throw new com.omnia.backend.common.exception.ResourceAlreadyExistsException(
                    "Llogaria nuk u krijua. Provo hyrjen perseri ose perdor hyrjen ekzistuese.");
        }
    }
}
