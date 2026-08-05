package com.omnia.backend.security.service;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class CurrentUserService {

    private static final Set<String>
            PLATFORM_ADMIN_ROLES =
            Set.of(
                    "SUPER_ADMIN",
                    "ADMIN"
            );

    private static final Set<String>
            PLATFORM_ADMIN_AUTHORITIES =
            Set.of(
                    "ROLE_SUPER_ADMIN",
                    "ROLE_ADMIN"
            );

    private final UserRepository userRepository;

    public CurrentUserService(
            UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    public User requireCurrentUser() {

        Authentication authentication =
                getAuthentication();

        if (!isAuthenticated(authentication)) {
            throw new AccessDeniedException(
                    "Authenticated user is required"
            );
        }

        return findCurrentUser()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Authenticated user not found"
                        )
                );
    }

    public Optional<User> findCurrentUser() {

        Authentication authentication =
                getAuthentication();

        if (!isAuthenticated(authentication)) {
            return Optional.empty();
        }

        String identifier =
                authentication.getName().trim();

        return userRepository
                .findByEmail(
                        identifier.toLowerCase(Locale.ROOT)
                )
                .or(() ->
                        userRepository.findByUsername(
                                identifier
                        )
                );
    }

    public boolean hasPlatformAdminAccess(
            User user
    ) {
        if (user == null
                || user.getRole() == null
                || user.getRole().getName() == null) {
            return false;
        }

        String roleName =
                user.getRole()
                        .getName()
                        .trim()
                        .toUpperCase(Locale.ROOT);

        return PLATFORM_ADMIN_ROLES.contains(roleName);
    }

    public boolean hasCurrentPlatformAdminAuthority() {

        Authentication authentication =
                getAuthentication();

        if (!isAuthenticated(authentication)) {
            return false;
        }

        return authentication
                .getAuthorities()
                .stream()
                .map(authority ->
                        authority.getAuthority()
                                .toUpperCase(Locale.ROOT)
                )
                .anyMatch(
                        PLATFORM_ADMIN_AUTHORITIES::contains
                );
    }

    public void requirePlatformAdmin() {

        if (hasCurrentPlatformAdminAuthority()) {
            return;
        }

        boolean platformAdministrator =
                findCurrentUser()
                        .map(this::hasPlatformAdminAccess)
                        .orElse(false);

        if (!platformAdministrator) {
            throw new AccessDeniedException(
                    "Platform administrator access is required"
            );
        }
    }

    private boolean isAuthenticated(
            Authentication authentication
    ) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(
                authentication.getPrincipal()
        );
    }
    private Authentication getAuthentication() {
        return SecurityContextHolder
                .getContext()
                .getAuthentication();
    }
}