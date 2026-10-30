package com.omnia.backend.service.impl;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.response.PlatformReportResponse;
import com.omnia.backend.dto.response.PlatformReportResponse.*;
import com.omnia.backend.entity.*;
import com.omnia.backend.security.service.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class PlatformReportService {
    private final CurrentUserService current;private final PlatformReportStore store;
    public PlatformReportService(CurrentUserService current,PlatformReportStore store){this.current=current;this.store=store;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PlatformReportResponse get(LocalDate start,LocalDate end,int days,Long scope){
        User actor=current.requireCurrentUser();
        if(actor.getRole()==null||!"SUPER_ADMIN".equalsIgnoreCase(actor.getRole().getName()))throw new AccessDeniedException("Super administrator required");
        PlatformReportWindow window=PlatformReportWindow.resolve(start,end,days,LocalDate.now(ZoneOffset.UTC));
        if(scope!=null&&scope<=0)throw new IllegalArgumentException("Organization id must be positive");
        List<Organization> organizations=store.organizations();Organization selected=null;
        if(scope!=null){selected=organizations.stream().filter(o->scope.equals(o.getId())).findFirst().orElseThrow(()->new ResourceNotFoundException("Organization not found"));}
        List<PlatformReportEvent> events=store.events(window,scope);
        List<OrganizationChoice> choices=organizations.stream().map(o->new OrganizationChoice(o.getId(),o.getName(),logo(o))).toList();
        long users=store.users(window.start(),window.exclusiveEnd(),scope),previousUsers=store.users(window.previousStart(),window.start(),scope);
        long companies=store.companies(window.start(),window.exclusiveEnd(),scope),previousCompanies=store.companies(window.previousStart(),window.start(),scope);
        return PlatformReportCalculator.aggregate(window,scope,selected==null?null:selected.getName(),choices,events,users,previousUsers,companies,previousCompanies);
    }
    private static String logo(Organization organization){return organization.getLogoFile()==null?null:"/api/files/"+organization.getLogoFile().getId();}
}
