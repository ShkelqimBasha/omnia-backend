package com.omnia.backend.service.impl;
import com.omnia.backend.dto.response.*;
import com.omnia.backend.dto.response.CompanyReportResponse.*;
import com.omnia.backend.dto.response.PlatformReportResponse.OrganizationChoice;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.*;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.security.service.OrganizationAccessService;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class CompanyReportService {
    private final OrganizationAccessService access;private final EntityManager em;private final PlatformReportStore reports;
    public CompanyReportService(OrganizationAccessService access,EntityManager em,PlatformReportStore reports){this.access=access;this.em=em;this.reports=reports;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CompanyReportResponse get(long id,LocalDate start,LocalDate end,int days){
        if(id<=0)throw new IllegalArgumentException("Company id must be positive");
        access.requireCanManageOrders(id);
        PlatformReportWindow w=PlatformReportWindow.resolve(start,end,days,LocalDate.now(ZoneOffset.UTC));
        Organization o=em.find(Organization.class,id);if(o==null)throw new ResourceNotFoundException("Company not found");
        String logo=o.getLogoFile()==null?null:"/api/files/"+o.getLogoFile().getId();
        var events=reports.events(w,id);var choices=List.of(new OrganizationChoice(id,o.getName(),logo));
        long customers=customers(id,w.start(),w.exclusiveEnd()),previousCustomers=customers(id,w.previousStart(),w.start());
        var report=PlatformReportCalculator.aggregate(w,id,o.getName(),choices,events,customers,previousCustomers,0,0);
        var previousWindow=new PlatformReportWindow(w.previousStart(),w.previousEnd(),w.days());
        var previous=PlatformReportCalculator.aggregate(previousWindow,id,o.getName(),choices,events,0,0,0,0).daily();
        Object[] inventory=em.createQuery("select count(p), sum(case when p.stock > 0 and p.stock < 5 then 1 else 0 end), sum(case when p.stock <= 0 then 1 else 0 end) from Product p where p.organization.id = :id and p.status = :active",Object[].class).setParameter("active",ProductStatus.ACTIVE).setParameter("id",id).getSingleResult();
        Object[] ratings=range(em.createQuery("select avg(r.rating), count(r) from Review r where r.product.organization.id = :id and r.hidden = false and r.createdAt >= :start and r.createdAt < :end",Object[].class),id,w.start(),w.exclusiveEnd()).getSingleResult();
        return new CompanyReportResponse(new Company(id,o.getName(),o.getSlug(),o.getStatus().name(),logo,o.getDescription(),o.getBusinessEmail(),o.getPhone(),o.getAddress()),report,previous,top(id,w),count(inventory[0]),count(inventory[1]),count(inventory[2]),ratings[0]==null?null:((Number)ratings[0]).doubleValue(),count(ratings[1]));
    }
    private long customers(long id,LocalDate a,LocalDate b){return range(em.createQuery("select count(distinct o.user.id) from Order o where o.organization.id = :id and o.status <> :cancelled and o.createdAt >= :start and o.createdAt < :end",Long.class).setParameter("cancelled",OrderStatus.CANCELLED),id,a,b).getSingleResult();}
    private List<TopProduct> top(long id,PlatformReportWindow w){
        // Three aggregates, then two bounded bulk lookups: no per-product query loop.
        var q=em.createQuery("select p.id, sum(i.quantity), sum(i.subtotal) from OrderItem i, Payment payment, Product p where payment.order = i.order and p.id = i.productId and p.organization.id = :id and i.order.organization.id = :id and payment.status = :paid and payment.paidAt >= :start and payment.paidAt < :end group by p.id order by sum(i.subtotal) desc, p.id asc",Object[].class).setParameter("paid",PaymentStatus.SUCCESS);
        var rows=range(q,id,w.start(),w.exclusiveEnd()).setMaxResults(3).getResultList();if(rows.isEmpty())return List.of();
        List<Long> ids=rows.stream().map(r->((Number)r[0]).longValue()).toList();
        Map<Long,Product> products=new HashMap<>();for(Product p:em.createQuery("select p from Product p join fetch p.category where p.organization.id = :id and p.id in :ids",Product.class).setParameter("id",id).setParameter("ids",ids).getResultList())products.put(p.getId(),p);
        Map<Long,String> images=new HashMap<>();for(ProductImage image:em.createQuery("select i from ProductImage i left join fetch i.uploadedFile where i.product.organization.id = :id and i.product.id in :ids order by i.isPrimary desc, i.id asc",ProductImage.class).setParameter("id",id).setParameter("ids",ids).getResultList())images.putIfAbsent(image.getProduct().getId(),image.getUploadedFile()==null?image.getLegacyImageUrl():"/api/files/"+image.getUploadedFile().getId());
        List<TopProduct> result=new ArrayList<>();for(Object[] row:rows){Product p=products.get(((Number)row[0]).longValue());if(p==null)continue;
            result.add(new TopProduct(p.getId(),p.getName(),images.get(p.getId()),count(row[1]),(BigDecimal)row[2],p.getPrice(),p.getDiscountPrice(),p.getDescription(),p.getCategory().getId(),p.getCategory().getName(),p.getBrand(),p.getStatus().name(),p.getStock()));}
        return List.copyOf(result);
    }
    private static long count(Object value){return value==null?0:((Number)value).longValue();}
    private static <T> TypedQuery<T> range(TypedQuery<T> q,long id,LocalDate a,LocalDate b){return q.setParameter("id",id).setParameter("start",a.atStartOfDay()).setParameter("end",b.atStartOfDay());}
}
