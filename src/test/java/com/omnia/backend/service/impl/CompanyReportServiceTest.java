package com.omnia.backend.service.impl;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.security.service.OrganizationAccessService;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyReportServiceTest {
    @Mock OrganizationAccessService access;@Mock EntityManager em;@Mock PlatformReportStore store;
    CompanyReportService service(){return new CompanyReportService(access,em,store);}
    @Test void deniedMembershipNeverReadsAnyReportData(){doThrow(new AccessDeniedException("Not a manager")).when(access).requireCanManageOrders(7L);assertThrows(AccessDeniedException.class,()->service().get(7,null,null,30));verifyNoInteractions(em,store);}
    @Test void invalidIdCannotBecomePlatformScope(){assertThrows(IllegalArgumentException.class,()->service().get(0,null,null,30));verifyNoInteractions(access,em,store);}
    @Test void missingCompanyNeverFallsBackToAnotherCompany(){assertThrows(ResourceNotFoundException.class,()->service().get(7,null,null,30));verify(access).requireCanManageOrders(7L);verifyNoInteractions(store);}
    @Test void invalidRangeRejectedBeforeDatabaseReads(){LocalDate day=LocalDate.now(java.time.ZoneOffset.UTC);assertThrows(IllegalArgumentException.class,()->service().get(7,day,day.minusDays(1),30));verifyNoInteractions(em,store);}
    @SuppressWarnings("unchecked") private void data(Long id,boolean noReviews){
        when(em.find(Organization.class,id)).thenReturn(Organization.builder().id(id).name("Selected company").slug("selected").build());
        TypedQuery<Long> customers=mock(TypedQuery.class);when(customers.setParameter(anyString(),any())).thenReturn(customers);when(customers.getSingleResult()).thenReturn(3L,1L);when(em.createQuery(anyString(),eq(Long.class))).thenReturn(customers);
        TypedQuery<Object[]> inventory=mock(TypedQuery.class),ratings=mock(TypedQuery.class),top=mock(TypedQuery.class);
        for(var q:List.of(inventory,ratings,top)){when(q.setParameter(anyString(),any())).thenReturn(q);}
        when(top.setMaxResults(3)).thenReturn(top);when(top.getResultList()).thenReturn(List.of());
        when(inventory.getSingleResult()).thenReturn(new Object[]{7L,1L,2L});when(ratings.getSingleResult()).thenReturn(noReviews?new Object[]{null,0L}:new Object[]{4.6,3L});
        when(em.createQuery(anyString(),eq(Object[].class))).thenAnswer(i->{String hql=i.getArgument(0);return hql.contains("from Review")?ratings:hql.contains("from OrderItem")?top:inventory;});
        LocalDate today=LocalDate.now(java.time.ZoneOffset.UTC);when(store.events(any(),eq(id))).thenReturn(List.of(new PlatformReportEvent(today,id,new BigDecimal("100"),2),new PlatformReportEvent(today,99L,new BigDecimal("999"),9)));
    }
    @Test void responseContainsOnlyRequestedCompanyAndRealSnapshot(){data(7L,false);var result=service().get(7,null,null,30);assertEquals(7L,result.company().id());assertEquals(1,result.report().organizations().size());assertEquals(new BigDecimal("100"),result.report().metrics().revenue().current());assertEquals(7,result.products());assertEquals(1,result.lowStock());assertEquals(4.6,result.averageRating());assertEquals(3,result.reviewCount());assertEquals(result.report().daily().size(),result.previousDaily().size());assertEquals(result.report().previousStart(),result.previousDaily().get(0).date());}
    @Test void unratedCompanyHasNoFabricatedAverage(){data(7L,true);var result=service().get(7,null,null,30);assertNull(result.averageRating());assertEquals(0,result.reviewCount());assertTrue(result.topProducts().isEmpty());}
}
