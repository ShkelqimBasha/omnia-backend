package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.*;
import com.omnia.backend.dto.request.*;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.*;
import com.omnia.backend.mapper.OrganizationMemberMapper;
import com.omnia.backend.repository.*;
import com.omnia.backend.security.service.*;
import com.omnia.backend.service.interfaces.EmailVerificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganizationMembersCoverage35Test {
    OrganizationRepository organizations;OrganizationMemberRepository members;UserRepository users;
    OrganizationAccessService access;CurrentUserService current;OrganizationMemberServiceImpl service;
    Organization org;User user;OrganizationMember member;
    @BeforeEach void setup() {
        organizations=mock(OrganizationRepository.class);members=mock(OrganizationMemberRepository.class);users=mock(UserRepository.class);
        access=mock(OrganizationAccessService.class);current=mock(CurrentUserService.class);
        service=new OrganizationMemberServiceImpl(organizations,members,users,mock(RoleRepository.class),mock(PasswordEncoder.class),
                mock(EmailVerificationService.class),new OrganizationMemberMapper(),access,current);
        org=Organization.builder().id(10L).name("Store").build();user=AdminUserCoverage35Test.user(2,"USER");
        when(organizations.findById(10L)).thenReturn(Optional.of(org));when(users.findById(2L)).thenReturn(Optional.of(user));
        when(current.requireCurrentUser()).thenReturn(AdminUserCoverage35Test.user(1,"SUPER_ADMIN"));
        member=OrganizationMember.builder().id(3L).organization(org).user(user).membershipRole(OrganizationMemberRole.STAFF)
                .status(OrganizationMemberStatus.ACTIVE).build();
        when(members.findById(3L)).thenReturn(Optional.of(member));
        when(members.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
    }
    OrganizationMemberRequest request() { return OrganizationMemberRequest.builder().userId(2L).membershipRole(OrganizationMemberRole.ADMIN).build(); }
    OrganizationMemberUpdateRequest update(OrganizationMemberRole role,OrganizationMemberStatus status) {
        return OrganizationMemberUpdateRequest.builder().membershipRole(role).status(status).build();
    }
    @Test void addVerifiedActiveUserCreatesMembershipWithoutChangingPlatformRole() {
        var r=service.addMember(10L,request());assertEquals(2L,r.getUserId());assertEquals(10L,r.getOrganizationId());
        assertEquals("ADMIN",r.getMembershipRole());assertEquals("ACTIVE",r.getStatus());assertEquals("USER",user.getRole().getName());
        verify(access).requireCanManageMembers(10L);
    }
    @Test void duplicateMembershipDoesNotWrite() {
        when(members.existsByOrganizationIdAndUserId(10L,2L)).thenReturn(true);
        assertThrows(ResourceAlreadyExistsException.class,()->service.addMember(10L,request()));verify(members,never()).saveAndFlush(any());
    }
    @Test void unknownUserAndCompanyAreRejected() {
        assertThrows(ResourceNotFoundException.class,()->service.addMember(99L,request()));
        when(users.findById(2L)).thenReturn(Optional.empty());assertThrows(ResourceNotFoundException.class,()->service.addMember(10L,request()));
    }
    @ParameterizedTest @EnumSource(value=UserStatus.class,names={"INACTIVE","BANNED"})
    void inactiveAndBannedUsersCannotJoinOrReactivate(UserStatus status) {
        user.setStatus(status);assertThrows(IllegalArgumentException.class,()->service.addMember(10L,request()));
        assertThrows(IllegalArgumentException.class,()->service.updateMember(10L,3L,update(OrganizationMemberRole.STAFF,OrganizationMemberStatus.ACTIVE)));
        verify(members,never()).saveAndFlush(any());
    }
    @ParameterizedTest @NullSource @ValueSource(booleans={false})
    void unverifiedUserCannotJoin(Boolean verified) {
        user.setEmailVerified(verified);assertThrows(IllegalArgumentException.class,()->service.addMember(10L,request()));
    }
    @Test void listRequiresMembershipManagementAccess() {
        when(members.findAllByOrganizationIdOrderByIdAsc(10L)).thenReturn(List.of(member));
        assertEquals(3L,service.getMembers(10L).getFirst().getId());verify(access).requireCanManageMembers(10L);
    }
    @Test void unauthorizedManagerCannotAddListOrUpdate() {
        doThrow(new AccessDeniedException("denied")).when(access).requireCanManageMembers(10L);
        assertThrows(AccessDeniedException.class,()->service.addMember(10L,request()));
        assertThrows(AccessDeniedException.class,()->service.getMembers(10L));
        assertThrows(AccessDeniedException.class,()->service.updateMember(10L,3L,update(OrganizationMemberRole.STAFF,OrganizationMemberStatus.ACTIVE)));
        verify(members,never()).saveAndFlush(any());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","a"})
    void candidateSearchRequiresTwoCharacters(String value) {
        assertThrows(IllegalArgumentException.class,()->service.searchMemberCandidates(10L,value));
        verify(users,never()).searchOrganizationMemberCandidates(anyLong(),any(),anyString(),any(Pageable.class));
    }
    @Test void oversizedCandidateSearchIsRejected() {
        assertThrows(IllegalArgumentException.class,()->service.searchMemberCandidates(10L,"a".repeat(101)));
    }
    @Test void candidateSearchTrimsQueryAndLimitsResults() {
        when(users.searchOrganizationMemberCandidates(eq(10L),eq(UserStatus.ACTIVE),eq("buyer"),any(Pageable.class))).thenReturn(List.of(user));
        var r=service.searchMemberCandidates(10L," buyer ");assertEquals("buyer2@example.com",r.getFirst().getEmail());
        verify(users).searchOrganizationMemberCandidates(eq(10L),eq(UserStatus.ACTIVE),eq("buyer"),argThat(p->p.getPageNumber()==0&&p.getPageSize()==20));
    }
    @Test void membersCannotBeUpdatedThroughAnotherCompany() {
        member.setOrganization(Organization.builder().id(20L).build());
        assertThrows(ResourceNotFoundException.class,()->service.updateMember(10L,3L,update(OrganizationMemberRole.ADMIN,OrganizationMemberStatus.ACTIVE)));
        verify(members,never()).saveAndFlush(any());
    }
    @Test void missingMemberIsNotFound() {
        assertThrows(ResourceNotFoundException.class,()->service.updateMember(10L,99L,update(OrganizationMemberRole.ADMIN,OrganizationMemberStatus.ACTIVE)));
    }
    @Test void memberRoleAndStatusAreUpdatedTogether() {
        var r=service.updateMember(10L,3L,update(OrganizationMemberRole.ADMIN,OrganizationMemberStatus.ACTIVE));
        assertEquals("ADMIN",r.getMembershipRole());verify(members).saveAndFlush(member);
    }
    @Test void lastOwnerCannotBeDemotedOrDeactivatedButCanRemainOwner() {
        member.setMembershipRole(OrganizationMemberRole.OWNER);
        when(members.countByOrganizationIdAndMembershipRoleAndStatus(10L,OrganizationMemberRole.OWNER,OrganizationMemberStatus.ACTIVE)).thenReturn(1L);
        assertThrows(IllegalArgumentException.class,()->service.updateMember(10L,3L,update(OrganizationMemberRole.STAFF,OrganizationMemberStatus.ACTIVE)));
        service.updateMember(10L,3L,update(OrganizationMemberRole.OWNER,OrganizationMemberStatus.ACTIVE));
        assertEquals(OrganizationMemberRole.OWNER,member.getMembershipRole());
    }
    @Test void anotherOwnerAllowsDemotion() {
        member.setMembershipRole(OrganizationMemberRole.OWNER);
        when(members.countByOrganizationIdAndMembershipRoleAndStatus(10L,OrganizationMemberRole.OWNER,OrganizationMemberStatus.ACTIVE)).thenReturn(2L);
        service.updateMember(10L,3L,update(OrganizationMemberRole.ADMIN,OrganizationMemberStatus.ACTIVE));assertEquals(OrganizationMemberRole.ADMIN,member.getMembershipRole());
    }
    @ParameterizedTest @NullSource @ValueSource(longs={0,-1})
    void invalidIdsAreRejected(Long id) {
        assertThrows(IllegalArgumentException.class,()->service.addMember(id,request()));
        assertThrows(IllegalArgumentException.class,()->service.getMembers(id));
        assertThrows(IllegalArgumentException.class,()->service.searchMemberCandidates(id,"buyer"));
        assertThrows(IllegalArgumentException.class,()->service.updateMember(id,3L,update(OrganizationMemberRole.ADMIN,OrganizationMemberStatus.ACTIVE)));
        assertThrows(IllegalArgumentException.class,()->service.updateMember(10L,id,update(OrganizationMemberRole.ADMIN,OrganizationMemberStatus.ACTIVE)));
    }
}
