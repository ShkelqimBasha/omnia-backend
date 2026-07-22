package com.omnia.backend.security.filter;

import com.omnia.backend.security.jwt.JwtService;
import com.omnia.backend.security.service.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN =
            "valid.jwt.token";

    private static final String USERNAME =
            "user@example.com";

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    @Mock
    private UserDetails userDetails;

    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        filter = new JwtAuthenticationFilter(
                jwtService,
                userDetailsService
        );

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_WithoutAuthorizationHeader_ShouldContinue()
            throws Exception {

        filter.doFilter(
                request,
                response,
                filterChain
        );

        verify(filterChain).doFilter(
                request,
                response
        );

        verifyNoInteractions(
                jwtService,
                userDetailsService
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isNull();
    }

    @Test
    void doFilter_WithNonBearerHeader_ShouldContinue()
            throws Exception {

        request.addHeader(
                "Authorization",
                "Basic credentials"
        );

        filter.doFilter(
                request,
                response,
                filterChain
        );

        verify(filterChain).doFilter(
                request,
                response
        );

        verifyNoInteractions(
                jwtService,
                userDetailsService
        );
    }

    @Test
    void doFilter_WithBlankBearerToken_ShouldContinue()
            throws Exception {

        request.addHeader(
                "Authorization",
                "Bearer    "
        );

        filter.doFilter(
                request,
                response,
                filterChain
        );

        verify(filterChain).doFilter(
                request,
                response
        );

        verifyNoInteractions(
                jwtService,
                userDetailsService
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isNull();
    }

    @Test
    void doFilter_WithExistingAuthentication_ShouldNotProcessToken()
            throws Exception {

        Authentication existingAuthentication =
                new TestingAuthenticationToken(
                        "existing-user",
                        null,
                        "ROLE_USER"
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        existingAuthentication
                );

        request.addHeader(
                "Authorization",
                "Bearer " + TOKEN
        );

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isSameAs(existingAuthentication);

        verifyNoInteractions(
                jwtService,
                userDetailsService
        );

        verify(filterChain).doFilter(
                request,
                response
        );
    }

    @Test
    void doFilter_WithValidToken_ShouldAuthenticateUser()
            throws Exception {

        request.setRemoteAddr("127.0.0.1");

        request.addHeader(
                "Authorization",
                "Bearer " + TOKEN
        );

        when(
                jwtService.extractUsername(TOKEN)
        ).thenReturn(USERNAME);

        when(
                userDetailsService.loadUserByUsername(
                        USERNAME
                )
        ).thenReturn(userDetails);

        when(
                userDetails.getUsername()
        ).thenReturn(USERNAME);

        when(
                jwtService.isTokenValid(
                        TOKEN,
                        USERNAME
                )
        ).thenReturn(true);

        when(
                userDetails.isEnabled()
        ).thenReturn(true);

        when(
                userDetails.isAccountNonLocked()
        ).thenReturn(true);

        when(
                userDetails.isAccountNonExpired()
        ).thenReturn(true);

        when(
                userDetails.isCredentialsNonExpired()
        ).thenReturn(true);

        doReturn(
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_USER"
                        )
                )
        ).when(userDetails).getAuthorities();

        filter.doFilter(
                request,
                response,
                filterChain
        );

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertThat(authentication)
                .isNotNull();

        assertThat(authentication.getPrincipal())
                .isSameAs(userDetails);

        assertThat(authentication.getCredentials())
                .isNull();

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");

        assertThat(authentication.getDetails())
                .isInstanceOf(
                        WebAuthenticationDetails.class
                );

        verify(filterChain).doFilter(
                request,
                response
        );
    }

    @Test
    void doFilter_WhenUsernameIsNull_ShouldRemainUnauthenticated()
            throws Exception {

        request.addHeader(
                "Authorization",
                "Bearer " + TOKEN
        );

        when(
                jwtService.extractUsername(TOKEN)
        ).thenReturn(null);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isNull();

        verify(
                userDetailsService,
                never()
        ).loadUserByUsername(
                org.mockito.ArgumentMatchers.anyString()
        );

        verify(
                jwtService,
                never()
        ).isTokenValid(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );

        verify(filterChain).doFilter(
                request,
                response
        );
    }

    @Test
    void doFilter_WhenUsernameIsBlank_ShouldRemainUnauthenticated()
            throws Exception {

        request.addHeader(
                "Authorization",
                "Bearer " + TOKEN
        );

        when(
                jwtService.extractUsername(TOKEN)
        ).thenReturn("   ");

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isNull();

        verifyNoInteractions(userDetailsService);

        verify(filterChain).doFilter(
                request,
                response
        );
    }

    @Test
    void doFilter_WithInvalidToken_ShouldNotAuthenticate()
            throws Exception {

        request.addHeader(
                "Authorization",
                "Bearer " + TOKEN
        );

        when(
                jwtService.extractUsername(TOKEN)
        ).thenReturn(USERNAME);

        when(
                userDetailsService.loadUserByUsername(
                        USERNAME
                )
        ).thenReturn(userDetails);

        when(
                userDetails.getUsername()
        ).thenReturn(USERNAME);

        when(
                jwtService.isTokenValid(
                        TOKEN,
                        USERNAME
                )
        ).thenReturn(false);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isNull();

        verify(filterChain).doFilter(
                request,
                response
        );
    }

    @Test
    void doFilter_WithMalformedToken_ShouldContinueUnauthenticated()
            throws Exception {

        request.addHeader(
                "Authorization",
                "Bearer malformed-token"
        );

        when(
                jwtService.extractUsername(
                        "malformed-token"
                )
        ).thenThrow(
                new JwtException(
                        "Malformed token"
                )
        );

        assertThatCode(
                () -> filter.doFilter(
                        request,
                        response,
                        filterChain
                )
        ).doesNotThrowAnyException();

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isNull();

        verifyNoInteractions(userDetailsService);

        verify(filterChain).doFilter(
                request,
                response
        );
    }

    @Test
    void doFilter_WithUnknownUser_ShouldContinueUnauthenticated()
            throws Exception {

        request.addHeader(
                "Authorization",
                "Bearer " + TOKEN
        );

        when(
                jwtService.extractUsername(TOKEN)
        ).thenReturn(USERNAME);

        when(
                userDetailsService.loadUserByUsername(
                        USERNAME
                )
        ).thenThrow(
                new UsernameNotFoundException(
                        "User not found"
                )
        );

        assertThatCode(
                () -> filter.doFilter(
                        request,
                        response,
                        filterChain
                )
        ).doesNotThrowAnyException();

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isNull();

        verify(filterChain).doFilter(
                request,
                response
        );
    }

    @Test
    void doFilter_WhenTokenParsingThrowsIllegalArgument_ShouldContinue()
            throws Exception {

        request.addHeader(
                "Authorization",
                "Bearer illegal-token"
        );

        when(
                jwtService.extractUsername(
                        "illegal-token"
                )
        ).thenThrow(
                new IllegalArgumentException(
                        "Invalid token argument"
                )
        );

        assertThatCode(
                () -> filter.doFilter(
                        request,
                        response,
                        filterChain
                )
        ).doesNotThrowAnyException();

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isNull();

        verify(filterChain).doFilter(
                request,
                response
        );
    }
}