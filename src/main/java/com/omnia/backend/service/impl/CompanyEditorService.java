package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.CompanyEditorRequest;
import com.omnia.backend.dto.response.CompanyEditorResponse;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.*;
import com.omnia.backend.repository.*;
import com.omnia.backend.security.service.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class CompanyEditorService {
    private final OrganizationRepository organizations;
    private final OrganizationMemberRepository members;
    private final UserRepository users;
    private final UploadedFileRepository files;
    private final CurrentUserService current;
    public CompanyEditorService(OrganizationRepository organizations,OrganizationMemberRepository members,
            UserRepository users,UploadedFileRepository files,CurrentUserService current){
        this.organizations=organizations;this.members=members;this.users=users;this.files=files;this.current=current;
    }
    private User administrator(){User user=current.requireCurrentUser();if(!current.hasPlatformAdminAccess(user))
        throw new AccessDeniedException("Platform administrator access is required");return user;}
    @Transactional(readOnly=true) public CompanyEditorResponse get(long id){administrator();return response(find(id));}
    @Transactional(readOnly=true) public List<CompanyEditorResponse.Administrator> candidates(String query){
        administrator();String q=clean(query);if(q.length()>100)throw new IllegalArgumentException("Search is too long");
        Specification<User> spec=(root,criteria,cb)->{
            var active=cb.and(cb.equal(root.get("status"),UserStatus.ACTIVE),cb.isTrue(root.get("emailVerified")));
            if(q.isEmpty())return active;
            String pattern="%"+q.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
            return cb.and(active,cb.or(cb.like(cb.lower(root.get("username")),pattern,'\\'),
                cb.like(cb.lower(root.get("email")),pattern,'\\'),cb.like(cb.lower(root.get("firstName")),pattern,'\\'),
                cb.like(cb.lower(root.get("lastName")),pattern,'\\')));
        };
        return users.findAll(spec,PageRequest.of(0,20,Sort.by("username"))).getContent().stream().map(this::candidate).toList();
    }
    @Transactional public CompanyEditorResponse save(Long id,CompanyEditorRequest request){
        User actor=administrator();boolean creating=id==null;
        boolean locationProvided=request.isLatitudeProvided()||request.isLongitudeProvided()
            ||request.getLatitude()!=null||request.getLongitude()!=null;
        if(request.isLatitudeProvided()!=request.isLongitudeProvided()
            ||!CompanyLocationRules.validPair(request.getLatitude(),request.getLongitude()))
            throw new IllegalArgumentException("Latitude and longitude must be provided together and within valid ranges");
        Organization company=creating?new Organization():organizations.findForEditor(id)
            .orElseThrow(()->new ResourceNotFoundException("Organization not found"));
        if(!creating&&(request.getRevision()==null||!request.getRevision().equals(company.getEditorRevision())))
            throw new ResourceAlreadyExistsException("Company changed since it was loaded. Reopen the editor before saving.");
        String name=clean(request.getName()).replaceAll("\\s+"," "),slug=clean(request.getSlug());
        if(name.isEmpty()||name.length()>150||!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")||slug.length()>160)
            throw new IllegalArgumentException("Invalid company name or slug");
        long except=creating?0L:id;
        if(organizations.existsByNameIgnoreCaseAndIdNot(name,except))throw new ResourceAlreadyExistsException("Company name already exists");
        if(organizations.existsBySlugIgnoreCaseAndIdNot(slug,except))throw new ResourceAlreadyExistsException("Company slug already exists");
        Long adminId=request.getAdministratorUserId();if(adminId==null||adminId<=0)throw new IllegalArgumentException("Administrator is required");
        User selected=users.findById(adminId).orElseThrow(()->new ResourceNotFoundException("Administrator not found"));
        if(selected.getDeletedAt()!=null||selected.getStatus()!=UserStatus.ACTIVE||!Boolean.TRUE.equals(selected.getEmailVerified()))
            throw new ResourceAlreadyExistsException("Administrator must be active and email-verified");
        OrganizationMember membership=creating?null:members.findByOrganizationIdAndUserId(id,adminId).orElse(null);
        if(membership!=null&&membership.getStatus()!=OrganizationMemberStatus.ACTIVE)
            throw new ResourceAlreadyExistsException("Selected administrator has an inactive company membership. Resolve it in Members first.");
        Long logoId=request.getLogoFileId();
        Long oldLogo=company.getLogoFile()==null?null:company.getLogoFile().getId();
        if(logoId!=null&&!logoId.equals(oldLogo)){
            UploadedFile logo=files.findById(logoId).orElseThrow(()->new ResourceNotFoundException("Logo file not found"));
            if(logo.getSize()==null||logo.getSize()<=0||logo.getSize()>5L*1024*1024||
                (logo.getContentType()==null||!Set.of("image/png","image/jpeg").contains(logo.getContentType())))throw new IllegalArgumentException("Logo must be PNG/JPG up to 5 MB");
            if(logo.getUploadedBy()==null||!actor.getId().equals(logo.getUploadedBy().getId()))
                throw new AccessDeniedException("New logo must be uploaded by the current administrator");
            company.setLogoFile(logo);
        }else if(logoId==null){company.setLogoFile(null);}
        company.setName(name);company.setEditorNameKey(name.toLowerCase(Locale.ROOT));company.setSlug(slug);company.setDescription(nullable(request.getDescription()));
        company.setBusinessEmail(nullable(request.getBusinessEmail()));company.setPhone(nullable(request.getPhone()));company.setAddress(nullable(request.getAddress()));
        if(locationProvided){company.setLatitude(request.getLatitude());company.setLongitude(request.getLongitude());}
        company.setStatus(OrganizationStatus.valueOf(request.getStatus()));company.setPrimaryAdministrator(selected);
        if(creating){company.setCreatedBy(actor);company.setEditorRevision(0L);}
        try {
            company=organizations.saveAndFlush(company);
            if(membership==null)membership=OrganizationMember.builder().organization(company).user(selected).createdBy(actor).build();
            // Grant the chosen administrator company ownership. Existing owners retain their roles.
            membership.setMembershipRole(OrganizationMemberRole.OWNER);membership.setStatus(OrganizationMemberStatus.ACTIVE);
            members.saveAndFlush(membership);
            return response(company);
        }catch(DataIntegrityViolationException conflict){throw new ResourceAlreadyExistsException("Company name, slug or administrator conflicts with existing data");}
    }
    private Organization find(long id){if(id<=0)throw new IllegalArgumentException("Invalid company id");return organizations.findById(id).orElseThrow(()->new ResourceNotFoundException("Organization not found"));}
    private CompanyEditorResponse response(Organization c){
        User admin=c.getPrimaryAdministrator();if(admin!=null)admin=users.findById(admin.getId()).orElse(null);return new CompanyEditorResponse(c.getId(),c.getName(),c.getSlug(),c.getDescription(),
            c.getBusinessEmail(),c.getPhone(),c.getAddress(),c.getLatitude(),c.getLongitude(),c.getStatus().name(),c.getLogoFile()==null?null:"/api/files/"+c.getLogoFile().getId(),
            c.getLogoFile()==null?null:c.getLogoFile().getId(),admin==null?null:candidate(admin),c.getEditorRevision()==null?0:c.getEditorRevision());
    }
    private CompanyEditorResponse.Administrator candidate(User u){String name=(clean(u.getFirstName())+" "+clean(u.getLastName())).trim();
        return new CompanyEditorResponse.Administrator(u.getId(),name.isEmpty()?u.getUsername():name,u.getEmail());}
    private static String clean(String s){return s==null?"":s.trim();}
    private static String nullable(String s){String clean=clean(s);return clean.isEmpty()?null:clean;}
}
