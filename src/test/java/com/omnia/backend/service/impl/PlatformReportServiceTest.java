package com.omnia.backend.service.impl;
import com.omnia.backend.entity.*;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformReportServiceTest {
    @Mock CurrentUserService current;@Mock PlatformReportStore store;
    private User actor(String role){return User.builder().id(1L).role(Role.builder().name(role).build()).build();}
    @Test void ordinaryAdminCannotQueryPlatformOrCompanyData(){when(current.requireCurrentUser()).thenReturn(actor("ADMIN"));var service=new PlatformReportService(current,store);assertThrows(AccessDeniedException.class,()->service.get(null,null,30,null));assertThrows(AccessDeniedException.class,()->service.get(null,null,30,1L));verifyNoInteractions(store);}
    @Test void organizationAdminCannotQueryPlatformData(){when(current.requireCurrentUser()).thenReturn(actor("ORG_ADMIN"));assertThrows(AccessDeniedException.class,()->new PlatformReportService(current,store).get(null,null,30,null));verifyNoInteractions(store);}
    @Test void unauthenticatedCallerCannotQueryAnything(){when(current.requireCurrentUser()).thenThrow(new AccessDeniedException("Login required"));assertThrows(AccessDeniedException.class,()->new PlatformReportService(current,store).get(null,null,30,null));verifyNoInteractions(store);}
    @Test void missingCompanyDoesNotFallBackToPlatform(){when(current.requireCurrentUser()).thenReturn(actor("SUPER_ADMIN"));when(store.organizations()).thenReturn(List.of());assertThrows(ResourceNotFoundException.class,()->new PlatformReportService(current,store).get(null,null,30,99L));verify(store,never()).events(any(),any());}
    @Test void negativeIdRejectedBeforeReadingStatistics(){when(current.requireCurrentUser()).thenReturn(actor("SUPER_ADMIN"));assertThrows(IllegalArgumentException.class,()->new PlatformReportService(current,store).get(null,null,30,-1L));verifyNoInteractions(store);}
}
