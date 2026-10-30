package com.omnia.backend.service.impl;
import com.omnia.backend.entity.*;
import com.omnia.backend.dto.request.*;
import com.omnia.backend.repository.*;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness=Strictness.LENIENT)
class AccountSettingsServiceTest {
    @Mock CurrentUserService current;@Mock UserRepository users;@Mock UserSettingsRepository settings;@Mock PasswordEncoder encoder;
    @Mock RefreshTokenService refresh;@Mock PasswordResetTokenRepository resets;@Mock EntityManager em;
    AccountSettingsService service;User actor;UserSettingsRequest request;
    @BeforeEach void setup(){actor=User.builder().id(7L).firstName("Real").lastName("User").phone("123").passwordHash("old-hash").build();
        when(current.requireCurrentUser()).thenReturn(actor);when(settings.findById(7L)).thenReturn(Optional.empty());
        when(settings.saveAndFlush(any())).thenAnswer(i->{UserSettings r=i.getArgument(0);if(r.getRevision()==null)r.setRevision(0L);return r;});
        service=new AccountSettingsService(current,users,settings,encoder,refresh,resets,em);
        request=UserSettingsRequest.builder().fullName(" Receiver ").phone("456").address("Tirane").theme("system").language("sq").currency("ALL").notifications(false).revision(-1L).build();}
    @Test void defaultsUseRealAccountAndNoFabricatedAddress(){var r=service.get();assertFalse(r.exists());assertEquals("Real User",r.fullName());assertEquals("123",r.phone());assertEquals("",r.address());assertEquals(-1,r.revision());}
    @Test void savesOnlyAuthenticatedUsersSettings(){var r=service.save(request);assertTrue(r.exists());assertEquals("Receiver",r.fullName());assertEquals("system",r.theme());assertEquals("ALL",r.currency());assertFalse(r.notifications());
        ArgumentCaptor<UserSettings> captor=ArgumentCaptor.forClass(UserSettings.class);verify(settings).saveAndFlush(captor.capture());assertEquals(7L,captor.getValue().getUserId());assertEquals("Real",actor.getFirstName());verify(users,never()).saveAndFlush(any());}
    @Test void rejectsStaleExistingRevision(){when(settings.findById(7L)).thenReturn(Optional.of(UserSettings.builder().userId(7L).revision(3L).build()));request.setRevision(2L);assertThrows(ResourceAlreadyExistsException.class,()->service.save(request));verify(settings,never()).saveAndFlush(any());}
    @Test void detectsAnotherClientCreatedTheFirstRow(){when(settings.findById(7L)).thenReturn(Optional.of(UserSettings.builder().userId(7L).revision(0L).build()));assertThrows(ResourceAlreadyExistsException.class,()->service.save(request));}
    @Test void cannotSaveBeforeReadingCurrentRevision(){request.setRevision(null);assertThrows(ResourceAlreadyExistsException.class,()->service.save(request));}
    @Test void wrongCurrentPasswordDoesNotWriteOrRevoke(){assertThrows(IllegalArgumentException.class,()->service.password(password("wrong","NewPass123")));verify(users,never()).saveAndFlush(any());verifyNoInteractions(refresh,resets);}
    @Test void samePasswordRejected(){when(encoder.matches("Current123","old-hash")).thenReturn(true);assertThrows(IllegalArgumentException.class,()->service.password(password("Current123","Current123")));verify(encoder,never()).encode(any());}
    @Test void shortAndOversizedUtf8PasswordsRejected(){assertThrows(IllegalArgumentException.class,()->service.password(password("old","short")));assertThrows(IllegalArgumentException.class,()->service.password(password("old","é".repeat(40))));verifyNoInteractions(refresh,resets);}
    @Test void successHashesPasswordAndRevokesRefreshAndResetTokens(){when(encoder.matches("Current123","old-hash")).thenReturn(true);when(encoder.encode("NewPass123")).thenReturn("new-hash");service.password(password("Current123","NewPass123"));
        assertEquals("new-hash",actor.getPasswordHash());verify(users).saveAndFlush(actor);verify(refresh).revokeAllUserTokens(7L);verify(resets).deleteByUserId(7L);}
    @Test void passwordsAreNeverTrimmed(){when(encoder.matches(" old password ","old-hash")).thenReturn(true);when(encoder.encode(" new password ")).thenReturn("new-hash");service.password(password(" old password "," new password "));verify(encoder).encode(" new password ");}
    @Test void unauthenticatedCallerCannotReadOrWrite(){when(current.requireCurrentUser()).thenThrow(new AccessDeniedException("Authentication required"));assertThrows(AccessDeniedException.class,()->service.get());assertThrows(AccessDeniedException.class,()->service.save(request));assertThrows(AccessDeniedException.class,()->service.password(password("old","NewPass123")));verifyNoInteractions(em,refresh,resets);}
    private ChangeCurrentPasswordRequest password(String current,String next){return ChangeCurrentPasswordRequest.builder().currentPassword(current).newPassword(next).build();}
}
