package com.omnia.backend.security.throttle;

import com.omnia.backend.security.handler.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-IP limits on the sign-in, sign-up, password and coupon endpoints. They are
 * generous because the website's server signs users in from one IP; the per-account
 * lockout in {@link LoginAttempts} is the main brute-force guard.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthThrottleFilter extends OncePerRequestFilter {

    record Rule(String name, int limit, Duration window) {
    }

    static final Rule LOGIN = new Rule("login", 60, Duration.ofMinutes(1));
    static final Rule REGISTER = new Rule("register", 40, Duration.ofMinutes(10));
    static final Rule PASSWORD = new Rule("password", 30, Duration.ofMinutes(10));
    static final Rule REFRESH = new Rule("refresh", 300, Duration.ofMinutes(1));
    static final Rule COUPON = new Rule("coupon", 60, Duration.ofMinutes(1));

    private static final String COUPON_PREFIX = "/api/coupons/";

    private final RateLimiter limiter;
    private final SecurityErrorResponseWriter errors;

    public AuthThrottleFilter(RateLimiter limiter, SecurityErrorResponseWriter errors) {
        this.limiter = limiter;
        this.errors = errors;
    }

    static Rule ruleFor(String method, String path) {
        if (path == null) return null;
        if ("POST".equals(method)) {
            return switch (path) {
                case "/api/auth/login", "/api/auth/google" -> LOGIN;
                case "/api/auth/register" -> REGISTER;
                case "/api/auth/forgot-password", "/api/auth/resend-verification",
                     "/api/auth/reset-password" -> PASSWORD;
                case "/api/auth/refresh" -> REFRESH;
                default -> null;
            };
        }
        if ("GET".equals(method) && path.startsWith(COUPON_PREFIX)
                && path.length() > COUPON_PREFIX.length()
                && path.indexOf('/', COUPON_PREFIX.length()) < 0) {
            return COUPON;
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Rule rule = ruleFor(request.getMethod(), request.getRequestURI());
        if (rule != null) {
            String key = "ip:" + rule.name() + ":" + ClientIp.of(request);
            if (!limiter.tryAcquire(key, rule.limit(), rule.window())) {
                long seconds = Math.max(1, limiter.millisUntilFree(key, rule.window()) / 1000);
                response.setHeader("Retry-After", String.valueOf(seconds));
                errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                        "Too many requests. Please try again later.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
