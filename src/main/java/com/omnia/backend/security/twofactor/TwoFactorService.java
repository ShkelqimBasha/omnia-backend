package com.omnia.backend.security.twofactor;

import com.omnia.backend.common.exception.InvalidCredentialsException;
import com.omnia.backend.common.exception.TooManyRequestsException;
import com.omnia.backend.dto.response.AuthResponse;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.jwt.JwtService;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-step sign-in. After the password (or Google) succeeds for an account with 2FA on,
 * sign-in answers with a short-lived challenge instead of tokens; the tokens come from
 * {@link #verify} with a code from the authenticator app or a one-time recovery code.
 */
@Service
public class TwoFactorService {

    public static final String ISSUER = "Omnia";
    static final int RECOVERY_CODES = 10;
    static final long CHALLENGE_SECONDS = 5 * 60;
    static final int CHALLENGE_ATTEMPTS = 5;
    /** Wrong codes per account before the second step is refused for a while. */
    static final int ACCOUNT_FAILURES = 10;
    static final java.time.Duration FAILURE_WINDOW = java.time.Duration.ofMinutes(15);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    record Challenge(long userId, long expiresAtMillis, int attemptsLeft) {
    }

    public record Setup(String secret, String otpauthUri) {
    }

    public record Status(boolean enabled, int recoveryCodesLeft) {
    }

    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final JwtService jwt;
    private final RefreshTokenService refresh;
    private final CurrentUserService currentUser;
    private final Clock clock;
    private com.omnia.backend.security.throttle.RateLimiter limiter;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLimiter(com.omnia.backend.security.throttle.RateLimiter limiter) {
        this.limiter = limiter;
    }

    public TwoFactorService(JdbcTemplate jdbc, UserRepository users, JwtService jwt,
                            RefreshTokenService refresh, CurrentUserService currentUser) {
        this(jdbc, users, jwt, refresh, currentUser, Clock.systemUTC());
    }

    TwoFactorService(JdbcTemplate jdbc, UserRepository users, JwtService jwt,
                     RefreshTokenService refresh, CurrentUserService currentUser, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.jwt = jwt;
        this.refresh = refresh;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ---- sign-in ----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public boolean isEnabled(Long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_two_factor WHERE user_id = ? AND enabled_at IS NOT NULL",
                Integer.class, userId);
        return count != null && count > 0;
    }

    /** The answer to a successful first step: a challenge when 2FA is on, otherwise null. */
    public AuthResponse challengeIfEnabled(User user) {
        if (user == null || user.getId() == null || !isEnabled(user.getId())) return null;
        purgeExpiredChallenges();
        String token = randomToken();
        challenges.put(token, new Challenge(user.getId(),
                clock.millis() + CHALLENGE_SECONDS * 1000, CHALLENGE_ATTEMPTS));
        return AuthResponse.builder()
                .twoFactorRequired(true)
                .challengeToken(token)
                .message("Two-step verification required")
                .build();
    }

    /** Second step: a 6-digit app code or a recovery code turns the challenge into tokens. */
    @Transactional
    public AuthResponse verify(String challengeToken, String code) {
        Challenge challenge = challengeToken == null ? null : challenges.get(challengeToken);
        if (challenge == null || challenge.expiresAtMillis() < clock.millis()) {
            if (challengeToken != null) challenges.remove(challengeToken);
            throw new InvalidCredentialsException("The sign-in step expired. Sign in again.");
        }
        String failKey = "2fa-fail:" + challenge.userId();
        if (limiter != null && limiter.count(failKey, FAILURE_WINDOW) >= ACCOUNT_FAILURES) {
            challenges.remove(challengeToken);
            throw new TooManyRequestsException("Too many wrong codes. Try again in 15 minutes.");
        }
        if (!checkCode(challenge.userId(), code)) {
            if (limiter != null) limiter.tryAcquire(failKey, Integer.MAX_VALUE, FAILURE_WINDOW);
            int left = challenge.attemptsLeft() - 1;
            if (left <= 0) {
                challenges.remove(challengeToken);
                throw new TooManyRequestsException("Too many wrong codes. Sign in again.");
            }
            challenges.put(challengeToken, new Challenge(challenge.userId(), challenge.expiresAtMillis(), left));
            throw new InvalidCredentialsException("The code is wrong.");
        }
        challenges.remove(challengeToken);
        User user = users.findById(challenge.userId())
                .orElseThrow(() -> new InvalidCredentialsException("Sign in again."));
        return AuthResponse.builder()
                .accessToken(jwt.generateToken(user.getEmail()))
                .refreshToken(refresh.createRefreshToken(user))
                .message("Login successful")
                .build();
    }

    // ---- settings (signed-in user) ----------------------------------------------------------

    @Transactional(readOnly = true)
    public Status status() {
        User me = currentUser.requireCurrentUser();
        Integer left = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_two_factor_recovery WHERE user_id = ? AND used_at IS NULL",
                Integer.class, me.getId());
        return new Status(isEnabled(me.getId()), left == null ? 0 : left);
    }

    /** Starts (or restarts) setup with a new secret; 2FA stays off until {@link #enable}. */
    @Transactional
    public Setup setup() {
        User me = currentUser.requireCurrentUser();
        if (isEnabled(me.getId())) {
            throw new IllegalArgumentException("Two-step verification is already on.");
        }
        String secret = Totp.newSecret();
        jdbc.update("DELETE FROM user_two_factor WHERE user_id = ?", me.getId());
        jdbc.update("INSERT INTO user_two_factor (user_id, secret, enabled_at, last_used_step, created_at) "
                + "VALUES (?, ?, NULL, 0, ?)", me.getId(), secret, now());
        return new Setup(secret, otpauthUri(me.getEmail(), secret));
    }

    /** Confirms setup with a code from the app; returns the recovery codes (shown once). */
    @Transactional
    public List<String> enable(String code) {
        User me = currentUser.requireCurrentUser();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT secret, enabled_at FROM user_two_factor WHERE user_id = ? FOR UPDATE", me.getId());
        if (rows.isEmpty()) throw new IllegalArgumentException("Start the setup first.");
        if (rows.get(0).get("enabled_at") != null) {
            throw new IllegalArgumentException("Two-step verification is already on.");
        }
        String secret = (String) rows.get(0).get("secret");
        long step = Totp.matchingStep(secret, code, nowStep(), 0);
        if (step < 0) throw new IllegalArgumentException("The code is wrong. Check the time on your phone.");
        jdbc.update("UPDATE user_two_factor SET enabled_at = ?, last_used_step = ? WHERE user_id = ?",
                now(), step, me.getId());
        return newRecoveryCodes(me.getId());
    }

    /** Turns 2FA off; needs a current app code or a recovery code. */
    @Transactional
    public void disable(String code) {
        User me = currentUser.requireCurrentUser();
        if (!isEnabled(me.getId())) return;
        if (!checkCode(me.getId(), code)) throw new IllegalArgumentException("The code is wrong.");
        jdbc.update("DELETE FROM user_two_factor_recovery WHERE user_id = ?", me.getId());
        jdbc.update("DELETE FROM user_two_factor WHERE user_id = ?", me.getId());
    }

    /** New recovery codes (the old ones stop working); needs a current app code. */
    @Transactional
    public List<String> regenerateRecoveryCodes(String code) {
        User me = currentUser.requireCurrentUser();
        if (!isEnabled(me.getId())) throw new IllegalArgumentException("Two-step verification is off.");
        if (!checkCode(me.getId(), code)) throw new IllegalArgumentException("The code is wrong.");
        return newRecoveryCodes(me.getId());
    }

    // ---- internals ----------------------------------------------------------------------

    /** App code (once per time step) or an unused recovery code. */
    boolean checkCode(long userId, String code) {
        if (code == null || code.isBlank()) return false;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT secret, last_used_step FROM user_two_factor WHERE user_id = ? AND enabled_at IS NOT NULL FOR UPDATE",
                userId);
        if (rows.isEmpty()) return false;
        String secret = (String) rows.get(0).get("secret");
        long lastUsed = ((Number) rows.get(0).get("last_used_step")).longValue();
        long step = Totp.matchingStep(secret, code, nowStep(), lastUsed);
        if (step >= 0) {
            jdbc.update("UPDATE user_two_factor SET last_used_step = ? WHERE user_id = ?", step, userId);
            return true;
        }
        String normalized = normalizeRecovery(code);
        if (normalized.length() != 8) return false;
        int used = jdbc.update("UPDATE user_two_factor_recovery SET used_at = ? "
                        + "WHERE user_id = ? AND code_hash = ? AND used_at IS NULL",
                now(), userId, sha256(normalized));
        return used == 1;
    }

    private List<String> newRecoveryCodes(long userId) {
        jdbc.update("DELETE FROM user_two_factor_recovery WHERE user_id = ?", userId);
        List<String> codes = new ArrayList<>();
        LocalDateTime now = now();
        while (codes.size() < RECOVERY_CODES) {
            StringBuilder raw = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                raw.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
            }
            String code = raw.substring(0, 4) + "-" + raw.substring(4);
            if (codes.contains(code)) continue;
            codes.add(code);
            jdbc.update("INSERT INTO user_two_factor_recovery (user_id, code_hash, used_at, created_at) "
                    + "VALUES (?, ?, NULL, ?)", userId, sha256(raw.toString()), now);
        }
        return codes;
    }

    static String normalizeRecovery(String code) {
        return code.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
    }

    static String otpauthUri(String email, String secret) {
        String label = java.net.URLEncoder.encode(ISSUER + ":" + email, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + ISSUER
                + "&algorithm=SHA1&digits=6&period=" + Totp.PERIOD_SECONDS;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private long nowStep() {
        return Totp.stepAt(clock.millis() / 1000);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void purgeExpiredChallenges() {
        long now = clock.millis();
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
