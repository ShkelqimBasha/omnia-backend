package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.*;
import com.omnia.backend.dto.request.*;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.*;
import com.omnia.backend.mapper.AdminUserMapper;
import com.omnia.backend.repository.*;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.interfaces.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserCoverage35Test {
    UserRepository users; RoleRepository roles; OrganizationMemberRepository members;
    CurrentUserService current; RefreshTokenService refresh; PasswordEncoder encoder;
    EmailVerificationService emails; AdminUserServiceImpl service; User admin;
    static User user(long id,String role) {
        return User.builder().id(id).firstName("Buyer").lastName("Test").username("buyer"+id)
                .email("buyer"+id+"@example.com").role(Role.builder().name(role).build())
                .status(UserStatus.ACTIVE).emailVerified(true).build();
    }
    @BeforeEach void setup() {
        users=mock(UserRepository.class);roles=mock(RoleRepository.class);members=mock(OrganizationMemberRepository.class);
        current=mock(CurrentUserService.class);refresh=mock(RefreshTokenService.class);
        encoder=mock(PasswordEncoder.class);emails=mock(EmailVerificationService.class);
        service=new AdminUserServiceImpl(users,roles,members,new AdminUserMapper(),current,refresh,encoder,emails);
        admin=user(1,"SUPER_ADMIN");when(current.requireCurrentUser()).thenReturn(admin);
        when(current.hasPlatformAdminAccess(admin)).thenReturn(true);
        when(members.findDistinctAdminUserIds(any(),anySet())).thenReturn(Set.of(2L));
        when(users.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
    }
    private User target(String role) {
        User u=user(2,role);when(users.findById(2L)).thenReturn(Optional.of(u));return u;
    }
    private AdminCreateUserRequest request() {
        return AdminCreateUserRequest.builder().firstName(" Buyer ").lastName(" Test ")
                .username(" newuser ").email(" BUYER@EXAMPLE.COM ").password("Password!1").phone(" +355691234567 ").build();
    }
    @Test void creationNormalizesIdentityHashesPasswordAndRequiresEmailVerification() {
        when(roles.findByName("USER")).thenReturn(Optional.of(Role.builder().name("USER").build()));
        when(encoder.encode("Password!1")).thenReturn("encoded");
        var response=service.createUser(request());assertEquals("newuser",response.getUsername());
        var captor=ArgumentCaptor.forClass(User.class);verify(users).saveAndFlush(captor.capture());
        User u=captor.getValue();assertEquals("buyer@example.com",u.getEmail());assertEquals("Buyer",u.getFirstName());
        assertEquals("Test",u.getLastName());assertEquals("+355691234567",u.getPhone());assertEquals("encoded",u.getPasswordHash());
        assertFalse(u.getEmailVerified());assertEquals(UserStatus.ACTIVE,u.getStatus());verify(emails).createVerificationToken(u);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"   "})
    void absentOptionalFieldsAreStoredAsNull(String value) {
        when(roles.findByName("USER")).thenReturn(Optional.of(Role.builder().name("USER").build()));
        var r=request();r.setLastName(value);r.setPhone(value);service.createUser(r);
        var cap=ArgumentCaptor.forClass(User.class);verify(users).saveAndFlush(cap.capture());
        assertNull(cap.getValue().getLastName());assertNull(cap.getValue().getPhone());
    }
    @Test void duplicateEmailIsRejectedBeforeWriting() {
        when(users.existsByEmail("buyer@example.com")).thenReturn(true);
        assertThrows(ResourceAlreadyExistsException.class,()->service.createUser(request()));
        verify(users,never()).saveAndFlush(any());verifyNoInteractions(encoder,emails);
    }
    @Test void duplicateUsernameIsRejectedBeforeWriting() {
        when(users.existsByUsername("newuser")).thenReturn(true);
        assertThrows(ResourceAlreadyExistsException.class,()->service.createUser(request()));verify(users,never()).saveAndFlush(any());
    }
    @Test void missingDefaultRoleIsRejected() {
        assertThrows(ResourceNotFoundException.class,()->service.createUser(request()));verify(users,never()).saveAndFlush(any());
    }
    @Test void ordinaryUserCannotReadOrMutatePlatformUsers() {
        when(current.hasPlatformAdminAccess(admin)).thenReturn(false);
        assertThrows(AccessDeniedException.class,()->service.createUser(request()));
        assertThrows(AccessDeniedException.class,()->service.getStats());
        assertThrows(AccessDeniedException.class,()->service.getUserById(2L));
        verifyNoInteractions(users);
    }
    @ParameterizedTest @CsvSource({"-1,20","0,0","0,101"})
    void invalidPaginationDoesNotQueryUsers(int page,int size) {
        assertThrows(IllegalArgumentException.class,()->service.getUsers(page,size,"id","asc",null,null,null));verifyNoInteractions(users);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","passwordHash","bad"})
    void invalidSortFieldIsRejected(String field) {
        assertThrows(IllegalArgumentException.class,()->service.getUsers(0,20,field,"asc",null,null,null));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","up"})
    void invalidSortDirectionIsRejected(String dir) {
        assertThrows(IllegalArgumentException.class,()->service.getUsers(0,20,"id",dir,null,null,null));
    }
    @ParameterizedTest @ValueSource(strings={"asc"," DESC "})
    void paginatedUsersPreservePageMetadataAndOrganizationAdminFlag(String direction) {
        User u=user(2,"USER");
        when(users.findAll(any(Specification.class),any(Pageable.class)))
                .thenAnswer(i->new PageImpl<>(List.of(u),i.getArgument(1),41));
        var result=service.getUsers(1,20," EMAIL ",direction,"buyer",UserStatus.ACTIVE,"USER");
        assertEquals(1,result.page());assertEquals(41,result.totalElements());assertEquals(3,result.totalPages());
        assertTrue(result.content().getFirst().getOrganizationAdmin());
        var cap=ArgumentCaptor.forClass(Pageable.class);verify(users).findAll(any(Specification.class),cap.capture());
        assertEquals("email",cap.getValue().getSort().iterator().next().getProperty());
        assertEquals(direction.trim().equalsIgnoreCase("asc"),cap.getValue().getSort().iterator().next().isAscending());
    }
    @ParameterizedTest @NullSource @ValueSource(longs={0,-1})
    void invalidUserIdIsRejected(Long id) { assertThrows(IllegalArgumentException.class,()->service.getUserById(id)); }
    @Test void missingUserIsNotFound() { assertThrows(ResourceNotFoundException.class,()->service.getUserById(9L)); }
    @Test void singleUserIncludesOrganizationAdministration() {
        target("USER");assertTrue(service.getUserById(2L).getOrganizationAdmin());
    }
    @ParameterizedTest @EnumSource(UserStatus.class)
    void statusChangesRevokeSessionsOnlyWhenNotActive(UserStatus status) {
        User u=target("USER");service.updateUserStatus(2L,UserStatusUpdateRequest.builder().status(status).build());
        assertEquals(status,u.getStatus());verify(users).saveAndFlush(u);
        verify(refresh,status==UserStatus.ACTIVE?never():times(1)).revokeAllUserTokens(2L);
    }
    @Test void nullStatusIsRejected() {
        target("USER");assertThrows(IllegalArgumentException.class,()->service.updateUserStatus(2L,new UserStatusUpdateRequest()));
    }
    @Test void administratorMayKeepOwnAccountActiveButCannotBanIt() {
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        service.updateUserStatus(1L,UserStatusUpdateRequest.builder().status(UserStatus.ACTIVE).build());
        assertThrows(AccessDeniedException.class,()->service.updateUserStatus(1L,UserStatusUpdateRequest.builder().status(UserStatus.BANNED).build()));
    }
    @Test void lastActiveAdminCannotBeDisabled() {
        target("ADMIN");when(users.countByStatusAndRoleNameIn(eq(UserStatus.ACTIVE),anySet())).thenReturn(1L);
        assertThrows(IllegalArgumentException.class,()->service.updateUserStatus(2L,UserStatusUpdateRequest.builder().status(UserStatus.INACTIVE).build()));
        verify(users,never()).saveAndFlush(any());
    }
    @Test void anotherActiveAdminAllowsDeactivation() {
        User u=target(" admin ");when(users.countByStatusAndRoleNameIn(eq(UserStatus.ACTIVE),anySet())).thenReturn(2L);
        service.updateUserStatus(2L,UserStatusUpdateRequest.builder().status(UserStatus.INACTIVE).build());assertEquals(UserStatus.INACTIVE,u.getStatus());
    }
    @ParameterizedTest @EnumSource(PlatformRoleName.class)
    void roleUpdatesResolvePersistedRole(PlatformRoleName name) {
        User u=target("USER");Role role=Role.builder().name(name.name()).build();when(roles.findByName(name.name())).thenReturn(Optional.of(role));
        service.updateUserRole(2L,UserRoleUpdateRequest.builder().role(name).build());assertSame(role,u.getRole());verify(users).saveAndFlush(u);
    }
    @Test void nullAndMissingRolesAreRejected() {
        target("USER");assertThrows(IllegalArgumentException.class,()->service.updateUserRole(2L,new UserRoleUpdateRequest()));
        assertThrows(ResourceNotFoundException.class,()->service.updateUserRole(2L,UserRoleUpdateRequest.builder().role(PlatformRoleName.USER).build()));
    }
    @Test void adminCannotDemoteSelf() {
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        assertThrows(AccessDeniedException.class,()->service.updateUserRole(1L,UserRoleUpdateRequest.builder().role(PlatformRoleName.USER).build()));
    }
    @Test void lastAdminCannotBeDemotedButAnotherAdminAllowsIt() {
        User u=target("ADMIN");var r=UserRoleUpdateRequest.builder().role(PlatformRoleName.USER).build();
        when(users.countByStatusAndRoleNameIn(eq(UserStatus.ACTIVE),anySet())).thenReturn(1L);
        assertThrows(IllegalArgumentException.class,()->service.updateUserRole(2L,r));
        when(users.countByStatusAndRoleNameIn(eq(UserStatus.ACTIVE),anySet())).thenReturn(2L);
        when(roles.findByName("USER")).thenReturn(Optional.of(Role.builder().name("USER").build()));
        service.updateUserRole(2L,r);assertEquals("USER",u.getRole().getName());
    }
    @Test void superAdminPromotionRequiresActiveAndVerifiedAccount() {
        User u=target("USER");var r=UserRoleUpdateRequest.builder().role(PlatformRoleName.SUPER_ADMIN).build();u.setStatus(UserStatus.INACTIVE);
        assertThrows(IllegalArgumentException.class,()->service.updateUserRole(2L,r));u.setStatus(UserStatus.ACTIVE);u.setEmailVerified(false);
        assertThrows(IllegalArgumentException.class,()->service.updateUserRole(2L,r));verify(users,never()).saveAndFlush(any());
    }
    @Test void usersWithoutRoleCanStillBeDeactivated() {
        User u=target("USER");u.setRole(null);service.updateUserStatus(2L,UserStatusUpdateRequest.builder().status(UserStatus.INACTIVE).build());
        assertEquals(UserStatus.INACTIVE,u.getStatus());
    }
    @Test void statisticsUseRepositoryTotals() {
        when(users.count()).thenReturn(10L);when(users.countByStatus(UserStatus.ACTIVE)).thenReturn(7L);
        when(users.countByStatus(UserStatus.INACTIVE)).thenReturn(2L);when(users.countByStatus(UserStatus.BANNED)).thenReturn(1L);
        when(users.countByRoleNameIn(anySet())).thenReturn(3L);when(members.countDistinctAdminUsers(any(),anySet())).thenReturn(4L);
        var r=service.getStats();assertEquals(10,r.getTotalUsers());assertEquals(7,r.getActiveUsers());assertEquals(2,r.getInactiveUsers());
        assertEquals(1,r.getBannedUsers());assertEquals(3,r.getPlatformAdmins());assertEquals(4,r.getOrganizationAdmins());
    }
}
