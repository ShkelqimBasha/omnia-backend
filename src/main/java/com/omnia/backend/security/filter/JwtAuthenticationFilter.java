package com.omnia.backend.security.filter;

import com.omnia.backend.security.jwt.JwtService;
import com.omnia.backend.security.service.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER =
            "Authorization";

    private static final String BEARER_PREFIX =
            "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader(
                        AUTHORIZATION_HEADER
                );

        if (!hasBearerToken(authorizationHeader)
                || SecurityContextHolder
                .getContext()
                .getAuthentication() != null) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader
                .substring(BEARER_PREFIX.length())
                .trim();

        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            authenticateRequest(
                    token,
                    request
            );
        } catch (
                JwtException
                | IllegalArgumentException
                | UsernameNotFoundException ignored
        ) {
            /*
             * Never log or return the raw JWT.
             * Invalid tokens remain unauthenticated.
             */
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasBearerToken(
            String authorizationHeader
    ) {
        return StringUtils.hasText(authorizationHeader)
                && authorizationHeader.startsWith(
                BEARER_PREFIX
        );
    }

    private void authenticateRequest(
            String token,
            HttpServletRequest request
    ) {
        String username =
                jwtService.extractUsername(token);

        if (!StringUtils.hasText(username)) {
            return;
        }

        UserDetails userDetails =
                userDetailsService.loadUserByUsername(
                        username
                );

        if (!jwtService.isTokenValid(
                token,
                userDetails.getUsername()
        )) {
            return;
        }

        if (!isAccountAllowed(userDetails)) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

        authentication.setDetails(
                new WebAuthenticationDetailsSource()
                        .buildDetails(request)
        );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);
    }

    private boolean isAccountAllowed(
            UserDetails userDetails
    ) {
        return userDetails.isEnabled()
                && userDetails.isAccountNonLocked()
                && userDetails.isAccountNonExpired()
                && userDetails.isCredentialsNonExpired();
    }
}