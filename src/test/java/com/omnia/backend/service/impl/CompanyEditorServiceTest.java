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
class CompanyEditorServiceTest {
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
    @Test void savesFieldsAndGrantsCompanyOwnerWithoutChangingGlobalRole(){
        var response=service.save(9L,request);assertEquals("New name",response.name());assertEquals("new-name",response.slug());
        assertEquals("info@example.com",response.businessEmail());assertEquals("Tirane",response.address());assertEquals(2L,response.administrator().id());
        ArgumentCaptor<OrganizationMember> member=ArgumentCaptor.forClass(OrganizationMember.class);verify(members).saveAndFlush(member.capture());
        assertEquals(OrganizationMemberRole.OWNER,member.getValue().getMembershipRole());assertNull(selected.getRole());
        assertEquals("new name",company.getEditorNameKey());
    }
    @Test void createUsesCurrentActorAndInitialRevision(){var response=service.save(null,request);assertEquals(9L,response.id());
        ArgumentCaptor<Organization> captor=ArgumentCaptor.forClass(Organization.class);verify(organizations).saveAndFlush(captor.capture());assertSame(actor,captor.getValue().getCreatedBy());assertEquals(0L,captor.getValue().getEditorRevision());}
    @Test void rejectsNonPlatformAdministrator(){when(current.hasPlatformAdminAccess(actor)).thenReturn(false);assertThrows(AccessDeniedException.class,()->service.save(9L,request));verify(organizations,never()).saveAndFlush(any());}
    @Test void rejectsNameConflict(){when(organizations.existsByNameIgnoreCaseAndIdNot("New name",9L)).thenReturn(true);assertThrows(ResourceAlreadyExistsException.class,()->service.save(9L,request));verify(organizations,never()).saveAndFlush(any());}
    @Test void rejectsSlugConflict(){when(organizations.existsBySlugIgnoreCaseAndIdNot("new-name",9L)).thenReturn(true);assertThrows(ResourceAlreadyExistsException.class,()->service.save(9L,request));verify(organizations,never()).saveAndFlush(any());}
    @Test void rejectsStaleRevision(){request.setRevision(2L);assertThrows(ResourceAlreadyExistsException.class,()->service.save(9L,request));verify(organizations,never()).saveAndFlush(any());}
    @Test void rejectsMissingRevisionOnEdit(){request.setRevision(null);assertThrows(ResourceAlreadyExistsException.class,()->service.save(9L,request));}
    @Test void rejectsUnverifiedAdministrator(){selected.setEmailVerified(false);assertThrows(ResourceAlreadyExistsException.class,()->service.save(9L,request));verify(members,never()).saveAndFlush(any());}
    @Test void rejectsDeletedAdministrator(){selected.setDeletedAt(java.time.LocalDateTime.now());assertThrows(ResourceAlreadyExistsException.class,()->service.save(9L,request));}
    @Test void rejectsInactiveMembershipWithoutReactivatingIt(){when(members.findByOrganizationIdAndUserId(9L,2L)).thenReturn(Optional.of(OrganizationMember.builder().status(OrganizationMemberStatus.INACTIVE).build()));assertThrows(ResourceAlreadyExistsException.class,()->service.save(9L,request));verify(members,never()).saveAndFlush(any());}
    @Test void preservesExistingLogoEvenIfLegacyWebp(){company.setLogoFile(UploadedFile.builder().id(8L).contentType("image/webp").size(123L).build());request.setLogoFileId(8L);assertEquals(8L,service.save(9L,request).logoFileId());verifyNoInteractions(files);}
    @Test void acceptsOwnedPngAtFiveMb(){UploadedFile logo=UploadedFile.builder().id(8L).contentType("image/png").size(5L*1024*1024).uploadedBy(actor).build();when(files.findById(8L)).thenReturn(Optional.of(logo));request.setLogoFileId(8L);assertEquals(8L,service.save(9L,request).logoFileId());}
    @Test void rejectsOversizedLogo(){UploadedFile logo=UploadedFile.builder().id(8L).contentType("image/png").size(5L*1024*1024+1).uploadedBy(actor).build();when(files.findById(8L)).thenReturn(Optional.of(logo));request.setLogoFileId(8L);assertThrows(IllegalArgumentException.class,()->service.save(9L,request));}
    @Test void rejectsNullMime(){UploadedFile logo=UploadedFile.builder().id(8L).size(123L).uploadedBy(actor).build();when(files.findById(8L)).thenReturn(Optional.of(logo));request.setLogoFileId(8L);assertThrows(IllegalArgumentException.class,()->service.save(9L,request));}
    @Test void rejectsOtherUploadOwner(){UploadedFile logo=UploadedFile.builder().id(8L).size(123L).contentType("image/jpeg").uploadedBy(selected).build();when(files.findById(8L)).thenReturn(Optional.of(logo));request.setLogoFileId(8L);assertThrows(AccessDeniedException.class,()->service.save(9L,request));}
    @Test void clearsLogoWhenExplicitlyRemoved(){company.setLogoFile(UploadedFile.builder().id(8L).build());request.setLogoFileId(null);assertNull(service.save(9L,request).logoFileId());}
    @Test void editingDoesNotDemoteOrDeleteOtherOwners(){service.save(9L,request);verify(members,never()).delete(any());verify(members,never()).findAllByOrganizationIdOrderByIdAsc(any());}
}
