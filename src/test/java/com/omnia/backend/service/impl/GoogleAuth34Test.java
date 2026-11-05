package com.omnia.backend.service.impl;

import com.google.firebase.auth.FirebaseToken;
import com.omnia.backend.common.exception.InvalidCredentialsException;
import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.jwt.JwtService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleAuth34Test {
    GoogleTokenVerifier34 verifier;
    UserRepository users;
    RoleRepository roles;
    PasswordEncoder passwords;
    JwtService jwt;
    RefreshTokenService refresh;
    GoogleAuthService34 service;
    @BeforeEach void setup() {
        verifier=mock(GoogleTokenVerifier34.class); users=mock(UserRepository.class);
        roles=mock(RoleRepository.class); passwords=mock(PasswordEncoder.class);
        jwt=mock(JwtService.class); refresh=mock(RefreshTokenService.class);
        service=new GoogleAuthService34(verifier,users,roles,passwords,jwt,refresh);
        when(verifier.verify("token")).thenReturn(new GoogleIdentity34("uid","buyer@example.com","Buyer"));
        when(users.findByGoogleUid34("uid")).thenReturn(Optional.empty());
    }
    @Test void invalidTokenCannotQueryUsersOrIssueSession() {
        when(verifier.verify("bad")).thenThrow(new InvalidCredentialsException("invalid"));
        assertThrows(InvalidCredentialsException.class,()->service.authenticate("bad",true));
        verifyNoInteractions(users,jwt,refresh);
    }
    @Test void existingEmailIsNeverAutomaticallyLinkedOrModified() {
        when(users.existsByEmail("buyer@example.com")).thenReturn(true);
        assertThrows(ResourceAlreadyExistsException.class,()->service.authenticate("token",true));
        verify(users,never()).saveAndFlush(any());
        verifyNoInteractions(jwt,refresh,passwords);
    }
    @Test void newAccountNeedsExplicitPrivacyConsent() {
        assertThrows(ResourceAlreadyExistsException.class,()->service.authenticate("token",false));
        verify(users,never()).saveAndFlush(any()); verifyNoInteractions(jwt,refresh);
    }
    @Test void newAccountHasUserRoleVerifiedIdentityAndUnusableRandomPassword() {
        Role role=mock(Role.class);
        when(roles.findByName("USER")).thenReturn(Optional.of(role));
        when(passwords.encode(anyString())).thenReturn("random-hash");
        when(users.saveAndFlush(any())).thenAnswer(invocation->invocation.getArgument(0));
        when(jwt.generateToken("buyer@example.com")).thenReturn("omnia-access");
        when(refresh.createRefreshToken(any())).thenReturn("omnia-refresh");
        var response=service.authenticate("token",true);
        assertEquals("omnia-access",response.getAccessToken());
        assertEquals("omnia-refresh",response.getRefreshToken());
        var captor=org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        User user=captor.getValue();
        assertEquals(role,user.getRole()); assertEquals("uid",user.getGoogleUid34());
        assertEquals(UserStatus.ACTIVE,user.getStatus()); assertTrue(user.getEmailVerified());
        assertEquals("random-hash",user.getPasswordHash());
        assertTrue(user.getUsername().startsWith("Buyer_"));
    }
    private User existing(UserStatus status) {
        User user=User.builder().email("buyer@example.com").googleUid34("uid")
                .status(status).emailVerified(true).build();
        when(users.findByGoogleUid34("uid")).thenReturn(Optional.of(user)); return user;
    }
    @Test void returningUserUsesExistingOmniaAccountWithoutConsentOrNewUser() {
        User user=existing(UserStatus.ACTIVE);
        when(jwt.generateToken(user.getEmail())).thenReturn("access");
        assertEquals("access",service.authenticate("token",false).getAccessToken());
        verify(users,never()).saveAndFlush(any()); verify(refresh).createRefreshToken(user);
    }
    @Test void bannedUserCannotGetSession() {
        existing(UserStatus.BANNED);
        assertThrows(InvalidCredentialsException.class,()->service.authenticate("token",true));
        verifyNoInteractions(jwt,refresh);
    }
    @Test void inactiveUserCannotGetSession() {
        existing(UserStatus.INACTIVE);
        assertThrows(InvalidCredentialsException.class,()->service.authenticate("token",true));
        verifyNoInteractions(jwt,refresh);
    }
    @Test void changedEmailDoesNotReassignAnExistingAccount() {
        existing(UserStatus.ACTIVE).setEmail("another@example.com");
        assertThrows(InvalidCredentialsException.class,()->service.authenticate("token",true));
        verifyNoInteractions(jwt,refresh);
    }
    @Test void locallyUnverifiedAccountCannotGetSession() {
        existing(UserStatus.ACTIVE).setEmailVerified(false);
        assertThrows(InvalidCredentialsException.class,()->service.authenticate("token",true));
        verifyNoInteractions(jwt,refresh);
    }
    private FirebaseToken token(String provider,boolean verified,long time) {
        FirebaseToken token=mock(FirebaseToken.class);
        when(token.getClaims()).thenReturn(Map.of("firebase",Map.of("sign_in_provider",provider),"auth_time",time));
        when(token.isEmailVerified()).thenReturn(verified);
        when(token.getEmail()).thenReturn("Buyer@Example.com");
        when(token.getUid()).thenReturn("uid"); when(token.getName()).thenReturn("Buyer");
        return token;
    }
    @Test void onlyGoogleVerifiedFreshIdentityIsAccepted() {
        assertEquals("buyer@example.com",GoogleTokenVerifier34.validatedIdentity(token("google.com",true,1000),1100).email());
        assertThrows(InvalidCredentialsException.class,()->GoogleTokenVerifier34.validatedIdentity(token("password",true,1000),1100));
        assertThrows(InvalidCredentialsException.class,()->GoogleTokenVerifier34.validatedIdentity(token("google.com",false,1000),1100));
        assertThrows(InvalidCredentialsException.class,()->GoogleTokenVerifier34.validatedIdentity(token("google.com",true,1000),1400));
        assertThrows(InvalidCredentialsException.class,()->GoogleTokenVerifier34.validatedIdentity(token("google.com",true,1400),1000));
    }
    @Test void missingProviderClaimsAreRejected() {
        FirebaseToken token=token("google.com",true,1000);
        when(token.getClaims()).thenReturn(Map.of());
        assertThrows(InvalidCredentialsException.class,()->GoogleTokenVerifier34.validatedIdentity(token,1100));
    }
    @Test void featureIsDisabledWithoutExplicitServerConfiguration() {
        assertThrows(InvalidCredentialsException.class,()->new GoogleTokenVerifier34(false,"omnia-996c0").verify("token"));
    }
    @Test void emptyAndOversizedTokensAreRejectedBeforeNetwork() {
        var verifier=new GoogleTokenVerifier34(true,"omnia-996c0");
        assertThrows(InvalidCredentialsException.class,()->verifier.verify(""));
        assertThrows(InvalidCredentialsException.class,()->verifier.verify("x".repeat(16001)));
    }
}
