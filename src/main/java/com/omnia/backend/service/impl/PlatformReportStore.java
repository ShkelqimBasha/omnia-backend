package com.omnia.backend.service.impl;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.enums.PaymentStatus;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Repository
public class PlatformReportStore {
    private final EntityManager em;
    public PlatformReportStore(EntityManager em){this.em=em;}
    public List<Organization> organizations(){return em.createQuery("select o from Organization o left join fetch o.logoFile order by lower(o.name), o.id",Organization.class).getResultList();}
    public List<PlatformReportEvent> events(PlatformReportWindow window,Long scope){
        List<PlatformReportEvent> result=new ArrayList<>();
        String where=scope==null?"":" and p.order.organization.id = :scope";
        TypedQuery<Object[]> payments=em.createQuery("select function('date', p.paidAt), p.order.organization.id, sum(p.order.totalAmount) from Payment p where p.status = :status and p.paidAt >= :start and p.paidAt < :end"+where+" group by function('date', p.paidAt), p.order.organization.id",Object[].class);
        payments.setParameter("status",PaymentStatus.SUCCESS);payments.setParameter("start",window.previousStart().atStartOfDay());payments.setParameter("end",window.exclusiveEnd().atStartOfDay());if(scope!=null)payments.setParameter("scope",scope);
        for(Object[] row:payments.getResultList())result.add(new PlatformReportEvent(date(row[0]),id(row[1]),decimal(row[2]),0));
        where=scope==null?"":" and o.organization.id = :scope";
        TypedQuery<Object[]> orders=em.createQuery("select function('date', o.createdAt), o.organization.id, count(o) from Order o where o.createdAt >= :start and o.createdAt < :end"+where+" group by function('date', o.createdAt), o.organization.id",Object[].class);
        orders.setParameter("start",window.previousStart().atStartOfDay());orders.setParameter("end",window.exclusiveEnd().atStartOfDay());if(scope!=null)orders.setParameter("scope",scope);
        for(Object[] row:orders.getResultList())result.add(new PlatformReportEvent(date(row[0]),id(row[1]),BigDecimal.ZERO,((Number)row[2]).longValue()));return result;
    }
    public long users(LocalDate start,LocalDate exclusiveEnd,Long scope){
        String jpql=scope==null?"select count(u) from User u where u.createdAt >= :start and u.createdAt < :end":
                "select count(distinct u.id) from OrganizationMember m join m.user u where m.organization.id = :scope and m.createdAt >= :start and m.createdAt < :end";
        TypedQuery<Long> q=em.createQuery(jpql,Long.class).setParameter("start",start.atStartOfDay()).setParameter("end",exclusiveEnd.atStartOfDay());if(scope!=null)q.setParameter("scope",scope);return q.getSingleResult();
    }
    public long companies(LocalDate start,LocalDate exclusiveEnd,Long scope){
        TypedQuery<Long> q=em.createQuery("select count(o) from Organization o where o.createdAt >= :start and o.createdAt < :end"+(scope==null?"":" and o.id = :scope"),Long.class)
                .setParameter("start",start.atStartOfDay()).setParameter("end",exclusiveEnd.atStartOfDay());if(scope!=null)q.setParameter("scope",scope);return q.getSingleResult();
    }
    private static LocalDate date(Object value){if(value instanceof LocalDate d)return d;if(value instanceof java.sql.Date d)return d.toLocalDate();return LocalDate.parse(value.toString().substring(0,10));}
    private static Long id(Object value){return value==null?null:((Number)value).longValue();}
    private static BigDecimal decimal(Object value){return value==null?BigDecimal.ZERO:value instanceof BigDecimal d?d:new BigDecimal(value.toString());}
}
