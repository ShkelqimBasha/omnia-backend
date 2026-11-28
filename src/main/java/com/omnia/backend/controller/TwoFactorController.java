package com.omnia.backend.controller;

import com.omnia.backend.dto.response.AuthResponse;
import com.omnia.backend.security.twofactor.TwoFactorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Two-step sign-in. {@code /verify} is public (it finishes a sign-in); the rest need the
 * signed-in user.
 */
@RestController
@RequestMapping("/api/auth/2fa")
public class TwoFactorController {

    public record VerifyRequest(@NotBlank @Size(max = 200) String challengeToken,
                                @NotBlank @Size(max = 20) String code) {
    }

    public record CodeRequest(@NotBlank @Size(max = 20) String code) {
    }

    private final TwoFactorService twoFactor;

    public TwoFactorController(TwoFactorService twoFactor) {
        this.twoFactor = twoFactor;
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verify(@Valid @RequestBody VerifyRequest request) {
        return ResponseEntity.ok(twoFactor.verify(request.challengeToken(), request.code()));
    }

    @GetMapping("/status")
    public ResponseEntity<TwoFactorService.Status> status() {
        return ResponseEntity.ok(twoFactor.status());
    }

    @PostMapping("/setup")
    public ResponseEntity<TwoFactorService.Setup> setup() {
        return ResponseEntity.ok(twoFactor.setup());
    }

    @PostMapping("/enable")
    public ResponseEntity<Map<String, List<String>>> enable(@Valid @RequestBody CodeRequest request) {
        return ResponseEntity.ok(Map.of("recoveryCodes", twoFactor.enable(request.code())));
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@Valid @RequestBody CodeRequest request) {
        twoFactor.disable(request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recovery-codes")
    public ResponseEntity<Map<String, List<String>>> recoveryCodes(@Valid @RequestBody CodeRequest request) {
        return ResponseEntity.ok(Map.of("recoveryCodes", twoFactor.regenerateRecoveryCodes(request.code())));
    }
}
