package com.omnia.backend.mapper;

import com.omnia.backend.entity.*;
import com.omnia.backend.enums.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class IdentityMappersCoverage35Test {
    User user() { return User.builder().id(2L).firstName("Buyer").lastName("Last").username("buyer")
            .email("buyer@example.com").phone("123").emailVerified(true).status(UserStatus.ACTIVE).build(); }
    Organization org() { return Organization.builder().id(10L).name("Store").slug("store").build(); }
    @Test void nullInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new AdminUserMapper().toResponse(null,false));
        assertThrows(IllegalArgumentException.class,()->new OrganizationMemberMapper().toResponse(null));
        assertThrows(IllegalArgumentException.class,()->new OrganizationMemberMapper().toCandidateResponse(null));
        assertThrows(IllegalArgumentException.class,()->new OrganizationCategoryPermissionMapper().toResponse(null));
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"USER","ADMIN"," super_admin ","unknown"})
    void administratorAccessCombinesPlatformRoleAndOrganizationRole(String role) {
        User u=user();if(role!=null)u.setRole(Role.builder().name(role).build());
        boolean platform=role!=null&&(role.trim().equalsIgnoreCase("ADMIN")||role.trim().equalsIgnoreCase("SUPER_ADMIN"));
        for(boolean organizationAdmin:new boolean[]{false,true}) {
            var r=new AdminUserMapper().toResponse(u,organizationAdmin);
            assertEquals(platform||organizationAdmin,r.getHasAdminAccess());assertEquals(organizationAdmin,r.getOrganizationAdmin());
            assertEquals(role,r.getPlatformRole());assertEquals("buyer@example.com",r.getEmail());assertEquals("ACTIVE",r.getStatus());
        }
    }
    @Test void avatarFileTakesPrecedenceOverLegacyAndEmptyValuesAreSafe() {
        User u=user();u.setLegacyProfileImage("legacy");var mapper=new AdminUserMapper();assertEquals("legacy",mapper.toResponse(u,false).getAvatarUrl());
        u.setAvatarFile(UploadedFile.builder().id(8L).build());assertEquals("/api/files/8",mapper.toResponse(u,false).getAvatarUrl());
        u.setAvatarFile(null);u.setLegacyProfileImage("  ");u.setStatus(null);u.setRole(Role.builder().build());
        assertNull(mapper.toResponse(u,false).getAvatarUrl());assertNull(mapper.toResponse(u,false).getStatus());
        u.setLegacyProfileImage(null);assertNull(mapper.toResponse(u,false).getAvatarUrl());
    }
    @Test void memberResponseContainsIdentityAndMembershipFields() {
        User u=user();u.setRole(Role.builder().name("USER").build());
        var m=OrganizationMember.builder().id(3L).organization(org()).user(u).membershipRole(OrganizationMemberRole.STAFF).status(OrganizationMemberStatus.ACTIVE).build();
        var mapper=new OrganizationMemberMapper();var r=mapper.toResponse(m);
        assertEquals(3L,r.getId());assertEquals(10L,r.getOrganizationId());assertEquals("Store",r.getOrganizationName());assertEquals(2L,r.getUserId());
        assertEquals("Buyer",r.getFirstName());assertEquals("Last",r.getLastName());assertEquals("buyer",r.getUsername());assertEquals(u.getEmail(),r.getEmail());
        assertEquals("USER",r.getGlobalRole());assertEquals("STAFF",r.getMembershipRole());assertEquals("ACTIVE",r.getStatus());
        u.setRole(null);m.setMembershipRole(null);m.setStatus(null);r=mapper.toResponse(m);assertNull(r.getGlobalRole());assertNull(r.getMembershipRole());assertNull(r.getStatus());
        var candidate=mapper.toCandidateResponse(u);assertEquals(2L,candidate.getId());assertEquals(u.getEmail(),candidate.getEmail());assertEquals("buyer",candidate.getUsername());
    }
    @Test void permissionResponsePreservesIndependentPermissionsAndNullableGrantor() {
        var p=OrganizationCategoryPermission.builder().id(7L).organization(org()).category(Category.builder().id(5L).name("Clothes").build())
                .canCreate(true).canUpdate(false).canDelete(true).status(OrganizationPermissionStatus.ACTIVE).grantedBy(user()).build();
        var mapper=new OrganizationCategoryPermissionMapper();var r=mapper.toResponse(p);
        assertEquals(7L,r.getId());assertEquals(10L,r.getOrganizationId());assertEquals("Store",r.getOrganizationName());
        assertEquals(5L,r.getCategoryId());assertEquals("Clothes",r.getCategoryName());assertTrue(r.getCanCreate());assertFalse(r.getCanUpdate());assertTrue(r.getCanDelete());
        assertEquals("ACTIVE",r.getStatus());assertEquals(2L,r.getGrantedByUserId());
        p.setGrantedBy(null);p.setStatus(null);r=mapper.toResponse(p);assertNull(r.getGrantedByUserId());assertNull(r.getStatus());
    }
}
