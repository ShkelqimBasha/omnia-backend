package com.omnia.backend.service.impl;

import com.omnia.backend.entity.Organization;
import com.omnia.backend.enums.PaymentStatus;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformReportStoreCoverage35Test {
    EntityManager em;PlatformReportStore store;
    LocalDate start=LocalDate.of(2026,10,1),end=LocalDate.of(2026,10,31);
    @BeforeEach void setup(){em=mock(EntityManager.class);store=new PlatformReportStore(em);}
    @ParameterizedTest @NullSource @ValueSource(longs={10}) @SuppressWarnings("unchecked")
    void eventQueriesUseHalfOpenWindowsAndOptionalCompanyScope(Long scope) {
        TypedQuery<Object[]> paid=mock(TypedQuery.class),orders=mock(TypedQuery.class);
        when(em.createQuery(anyString(),eq(Object[].class))).thenReturn(paid,orders);
        when(paid.getResultList()).thenReturn(Arrays.asList(
                new Object[]{start,10L,new BigDecimal("38.50")},
                new Object[]{java.sql.Date.valueOf(start.plusDays(1)),11,12.25},
                new Object[]{"2026-10-03 12:00:00",null,null}));
        when(orders.getResultList()).thenReturn(Collections.singletonList(new Object[]{start,10L,3L}));
        var window=new PlatformReportWindow(start,end,31);var events=store.events(window,scope);
        assertEquals(4,events.size());assertEquals(new BigDecimal("38.50"),events.get(0).revenue());
        assertEquals(new BigDecimal("12.25"),events.get(1).revenue());assertNull(events.get(2).organizationId());
        assertEquals(BigDecimal.ZERO,events.get(2).revenue());assertEquals(3L,events.get(3).orders());
        verify(paid).setParameter("status",PaymentStatus.SUCCESS);verify(paid).setParameter("start",window.previousStart().atStartOfDay());
        verify(orders).setParameter("end",end.plusDays(1).atStartOfDay());
        var queries=org.mockito.ArgumentCaptor.forClass(String.class);verify(em,times(2)).createQuery(queries.capture(),eq(Object[].class));
        for(String jpql:queries.getAllValues())assertEquals(scope!=null,jpql.contains(":scope"));
        if(scope==null){verify(paid,never()).setParameter(eq("scope"),any());verify(orders,never()).setParameter(eq("scope"),any());}
        else{verify(paid).setParameter("scope",scope);verify(orders).setParameter("scope",scope);}
    }
    @ParameterizedTest @NullSource @ValueSource(longs={10}) @SuppressWarnings("unchecked")
    void userAndCompanyCountsHonorDateBoundsAndScope(Long scope) {
        TypedQuery<Long> query=mock(TypedQuery.class,RETURNS_SELF);when(em.createQuery(anyString(),eq(Long.class))).thenReturn(query);when(query.getSingleResult()).thenReturn(4L);
        assertEquals(4L,store.users(start,end,scope));assertEquals(4L,store.companies(start,end,scope));
        verify(query,times(2)).setParameter("start",start.atStartOfDay());verify(query,times(2)).setParameter("end",end.atStartOfDay());
        var sql=org.mockito.ArgumentCaptor.forClass(String.class);verify(em,times(2)).createQuery(sql.capture(),eq(Long.class));
        assertEquals(scope!=null,sql.getAllValues().getFirst().contains("count(distinct u.id)"));
        if(scope!=null)verify(query,times(2)).setParameter("scope",scope);else verify(query,never()).setParameter(eq("scope"),any());
    }
    @Test @SuppressWarnings("unchecked") void companyChoicesIncludeLogoAndStableSort() {
        TypedQuery<Organization> query=mock(TypedQuery.class);var org=Organization.builder().id(10L).name("Store").build();
        when(em.createQuery(anyString(),eq(Organization.class))).thenReturn(query);when(query.getResultList()).thenReturn(List.of(org));
        assertEquals(List.of(org),store.organizations());verify(em).createQuery(contains("left join fetch o.logoFile order by lower(o.name), o.id"),eq(Organization.class));
    }
}
