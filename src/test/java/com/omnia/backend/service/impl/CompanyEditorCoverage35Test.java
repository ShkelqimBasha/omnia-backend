package com.omnia.backend.service.impl;
import com.omnia.backend.common.exception.*;
import com.omnia.backend.dto.request.CompanyEditorRequest;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.*;
import com.omnia.backend.repository.*;
import com.omnia.backend.security.service.CurrentUserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness=Strictness.LENIENT)
class CompanyEditorCoverage35Test {
    @Mock OrganizationRepository organizations;@Mock OrganizationMemberRepository members;
    @Mock UserRepository users;@Mock UploadedFileRepository files;@Mock CurrentUserService current;
    CompanyEditorService service;User actor,selected;Organization company;CompanyEditorRequest request;
    @BeforeEach void setup(){
        service=new CompanyEditorService(organizations,members,users,files,current);
        actor=User.builder().id(1L).status(UserStatus.ACTIVE).emailVerified(true).build();
        selected=User.builder().id(2L).firstName("Owner").email("owner@example.com").status(UserStatus.ACTIVE).emailVerified(true).build();
        company=Organization.builder().id(9L).name("Old name").slug("old-name").createdBy(actor).editorRevision(3L).build();
        request=CompanyEditorRequest.builder().name(" New name ").slug("new-name").description(" Description ")
            .businessEmail("info@example.com").phone("+355 69 123").address("Tirane").status("ACTIVE").administratorUserId(2L).revision(3L).build();
        when(current.requireCurrentUser()).thenReturn(actor);when(current.hasPlatformAdminAccess(actor)).thenReturn(true);
        when(organizations.findForEditor(9L)).thenReturn(Optional.of(company));when(organizations.findById(9L)).thenReturn(Optional.of(company));
        when(users.findById(2L)).thenReturn(Optional.of(selected));when(members.findByOrganizationIdAndUserId(9L,2L)).thenReturn(Optional.empty());
        when(organizations.saveAndFlush(any())).thenAnswer(i->{Organization c=i.getArgument(0);if(c.getId()==null)c.setId(9L);return c;});
        when(members.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
    }

    @Test void existingMembershipIsPromotedWithoutCreatingDuplicate() {
        var member=OrganizationMember.builder().status(OrganizationMemberStatus.ACTIVE).membershipRole(OrganizationMemberRole.STAFF).build();
        when(members.findByOrganizationIdAndUserId(9L,2L)).thenReturn(Optional.of(member));
        service.save(9L,request);verify(members).saveAndFlush(member);assertEquals(OrganizationMemberRole.OWNER,member.getMembershipRole());
    }
    @Test void missingCompanyAndAdministratorFailWithoutWrites(){
        assertThrows(ResourceNotFoundException.class,()->service.save(99L,request));
        when(users.findById(2L)).thenReturn(Optional.empty());assertThrows(ResourceNotFoundException.class,()->service.save(9L,request));
        verify(organizations,never()).saveAndFlush(any());
    }
    @Test void invalidNamesAndSlugsAreRejected(){
        for(String name:new String[]{null," ","x".repeat(151)}){request.setName(name);assertThrows(IllegalArgumentException.class,()->service.save(9L,request));}
        request.setName("Store");for(String slug:new String[]{null,"Bad Slug","x".repeat(161)}){request.setSlug(slug);assertThrows(IllegalArgumentException.class,()->service.save(9L,request));}
        verify(organizations,never()).saveAndFlush(any());
    }
    @Test void missingOrInactiveAdministratorCannotOwnCompany(){
        for(Long id:new Long[]{null,0L,-1L}){request.setAdministratorUserId(id);assertThrows(IllegalArgumentException.class,()->service.save(9L,request));}
        request.setAdministratorUserId(2L);selected.setStatus(UserStatus.INACTIVE);assertThrows(ResourceAlreadyExistsException.class,()->service.save(9L,request));
        selected.setStatus(UserStatus.ACTIVE);selected.setEmailVerified(null);assertThrows(ResourceAlreadyExistsException.class,()->service.save(9L,request));
    }
    @Test void logoNeedsValidSizeMimeAndOwner(){
        request.setLogoFileId(8L);assertThrows(ResourceNotFoundException.class,()->service.save(9L,request));
        var file=UploadedFile.builder().id(8L).contentType("image/jpeg").uploadedBy(actor).build();when(files.findById(8L)).thenReturn(Optional.of(file));
        for(Long size:new Long[]{null,0L,-1L}){file.setSize(size);assertThrows(IllegalArgumentException.class,()->service.save(9L,request));}
        file.setSize(1L);file.setContentType("image/gif");assertThrows(IllegalArgumentException.class,()->service.save(9L,request));
        file.setContentType("image/jpeg");file.setUploadedBy(null);assertThrows(AccessDeniedException.class,()->service.save(9L,request));
        file.setUploadedBy(actor);assertEquals(8L,service.save(9L,request).logoFileId());
    }
    @Test void databaseUniquenessRaceIsReportedAsConflict(){
        doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate")).when(organizations).saveAndFlush(any());
        assertThrows(ResourceAlreadyExistsException.class,()->service.save(9L,request));verify(members,never()).saveAndFlush(any());
    }
    @Test void editorLookupValidatesIdAndHandlesRemovedAdministratorAndLegacyRevision(){
        assertThrows(IllegalArgumentException.class,()->service.get(0));assertThrows(ResourceNotFoundException.class,()->service.get(99));
        company.setPrimaryAdministrator(selected);company.setEditorRevision(null);when(users.findById(2L)).thenReturn(Optional.empty());
        var result=service.get(9);assertNull(result.administrator());assertEquals(0L,result.revision());
    }
    @Test @SuppressWarnings("unchecked") void candidateSearchIsBoundedAndEscapesLiteralWildcards(){
        var root=mock(jakarta.persistence.criteria.Root.class,RETURNS_DEEP_STUBS);
        var cb=mock(jakarta.persistence.criteria.CriteriaBuilder.class,RETURNS_DEEP_STUBS);
        when(users.findAll(any(org.springframework.data.jpa.domain.Specification.class),any(org.springframework.data.domain.Pageable.class)))
            .thenAnswer(i->{org.springframework.data.jpa.domain.Specification<User> spec=i.getArgument(0);spec.toPredicate(root,null,cb);return new org.springframework.data.domain.PageImpl<>(java.util.List.of(selected));});
        selected.setFirstName(null);selected.setLastName(" ");selected.setUsername("owner");
        assertEquals("owner",service.candidates(null).getFirst().name());
        assertEquals(1,service.candidates(" A%_ ").size());
        verify(cb,times(4)).like(any(),eq("%a\\%\\_%"),eq('\\'));
        verify(users,times(2)).findAll(any(org.springframework.data.jpa.domain.Specification.class),eq(org.springframework.data.domain.PageRequest.of(0,20,org.springframework.data.domain.Sort.by("username"))));
        assertThrows(IllegalArgumentException.class,()->service.candidates("x".repeat(101)));
    }
}
