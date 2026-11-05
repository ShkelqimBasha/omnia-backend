package com.omnia.backend.service.impl;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.omnia.backend.common.exception.InvalidCredentialsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class GoogleTokenVerifier34 {
    private final boolean enabled;
    private final String project;
    private volatile FirebaseAuth auth;

    public GoogleTokenVerifier34(
            @Value("${omnia.google.enabled:false}") boolean enabled,
            @Value("${omnia.google.project-id:omnia-996c0}") String project) {
        this.enabled = enabled;
        this.project = project;
    }

    public GoogleIdentity34 verify(String token) {
        if (!enabled) throw new InvalidCredentialsException("Hyrja me Google nuk eshte aktivizuar ne server.");
        // Never use an emulator or unsigned token verifier on this endpoint.
        if (System.getenv("FIREBASE_AUTH_EMULATOR_HOST") != null)
            throw new InvalidCredentialsException("Konfigurimi Google nuk eshte i vlefshem.");
        if (token == null || token.isBlank() || token.length() > 16000)
            throw invalid();
        try {
            FirebaseToken decoded = firebaseAuth().verifyIdToken(token, true);
            return validatedIdentity(decoded, Instant.now().getEpochSecond());
        } catch (Exception ignored) {
            // Tokens, email addresses and provider exception messages are not logged.
            throw invalid();
        }
    }

    static GoogleIdentity34 validatedIdentity(FirebaseToken token, long now) {
        Map<String,Object> claims = token.getClaims();
        Object firebase = claims.get("firebase");
        Object authTime = claims.get("auth_time");
        String email = token.getEmail();
        if (!(firebase instanceof Map<?,?> provider)
                || !"google.com".equals(provider.get("sign_in_provider"))
                || !token.isEmailVerified()
                || email == null || email.length() > 150 || !email.contains("@")
                || token.getUid() == null || token.getUid().isBlank()
                || token.getUid().length() > 128
                || !(authTime instanceof Number time)
                || time.longValue() > now + 60 || time.longValue() < now - 300)
            throw invalid();
        return new GoogleIdentity34(token.getUid(), email.trim().toLowerCase(Locale.ROOT), token.getName());
    }

    private synchronized FirebaseAuth firebaseAuth() throws java.io.IOException {
        if (auth == null) {
            FirebaseApp app;
            try { app = FirebaseApp.getInstance("omnia-google34"); }
            catch (IllegalStateException absent) {
                app = FirebaseApp.initializeApp(FirebaseOptions.builder()
                        .setProjectId(project)
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .build(), "omnia-google34");
            }
            auth = FirebaseAuth.getInstance(app);
        }
        return auth;
    }
    private static InvalidCredentialsException invalid() {
        return new InvalidCredentialsException("Identiteti Google nuk u verifikua. Provo hyrjen perseri.");
    }
}
