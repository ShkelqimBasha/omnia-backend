package com.omnia.backend.security.service;

import com.omnia.backend.entity.User;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class CustomUserDetailsService
        implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(
            UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(
            String usernameOrEmail
    ) throws UsernameNotFoundException {

        if (!StringUtils.hasText(usernameOrEmail)) {
            throw new UsernameNotFoundException(
                    "User not found"
            );
        }

        String normalizedIdentifier =
                usernameOrEmail.trim();

        User user = userRepository
                .findByEmail(
                        normalizedIdentifier.toLowerCase(
                                Locale.ROOT
                        )
                )
                .or(() ->
                        userRepository.findByUsername(
                                normalizedIdentifier
                        )
                )
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found"
                        )
                );

        boolean active =
                UserStatus.ACTIVE.equals(
                        user.getStatus()
                );

        boolean emailVerified =
                Boolean.TRUE.equals(
                        user.getEmailVerified()
                );

        boolean accountNonLocked =
                !UserStatus.BANNED.equals(
                        user.getStatus()
                );

        String authority =
                "ROLE_"
                        + user.getRole()
                        .getName()
                        .trim()
                        .toUpperCase(Locale.ROOT);

        return new org.springframework.security
                .core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                active && emailVerified,
                true,
                true,
                accountNonLocked,
                List.of(
                        new SimpleGrantedAuthority(
                                authority
                        )
                )
        );
    }
}