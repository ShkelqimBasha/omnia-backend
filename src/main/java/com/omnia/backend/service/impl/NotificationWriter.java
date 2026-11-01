package com.omnia.backend.service.impl;
import com.omnia.backend.entity.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import jakarta.persistence.EntityManager;
import java.util.*;

@Service
@Transactional(propagation=Propagation.MANDATORY)
public class NotificationWriter {
 private final JdbcTemplate jdbc; private final EntityManager em;
 public NotificationWriter(JdbcTemplate jdbc,EntityManager em){this.jdbc=jdbc;this.em=em;}
 static final String ADMIN="EXISTS (SELECT 1 FROM roles r WHERE r.id=u.role_id AND r.name IN ('ADMIN','SUPER_ADMIN'))";
 private Set<Long> recipients(Long org,Long customer){
  String member="EXISTS (SELECT 1 FROM organization_members m JOIN organizations g ON g.id=m.organization_id WHERE m.user_id=u.id AND m.organization_id=? AND m.status='ACTIVE' AND m.membership_role IN ('OWNER','ADMIN') AND g.status='ACTIVE')";
  return new TreeSet<>(jdbc.queryForList("SELECT u.id FROM users u WHERE u.deleted_at IS NULL AND u.status='ACTIVE' AND ("+ADMIN+" OR u.id=? OR "+member+")",Long.class,customer==null?0:customer,org==null?0:org));
 }
 private void write(String key,String kind,String category,String type,long id,Long org,String label,String status,java.math.BigDecimal amount,Integer quantity,Long customer){
  // Flush pending JPA changes before JDBC reads/FKs. The same transaction rolls back both stores.
  em.flush();
  for(long user:recipients(org,customer))jdbc.update("INSERT INTO notification_inbox (recipient_id,event_key,kind,category,target_type,target_id,organization_id,label,status,amount,quantity,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE id=id",user,key,kind,category,type,id,org,label==null?"":label.substring(0,Math.min(200,label.length())),status,amount,quantity);
 }
 public void order(Order o){
  if(o==null||o.getId()==null)return;Long org=o.getOrganization()==null?null:o.getOrganization().getId();
  String status=o.getStatus().name();write("order:"+o.getId()+":"+status,status.equals("PENDING")?"ORDER_CREATED":"ORDER_STATUS","USER","ORDER",o.getId(),org,o.getOrganizationName(),status,o.getTotalAmount(),null,o.getUser().getId());
  if(status.equals("PENDING")){
   // Read final product stock once per product, after every line item has been applied.
   for(Product p:em.createQuery("select p from Product p where p.id in (select i.productId from OrderItem i where i.order.id=:id)",Product.class).setParameter("id",o.getId()).getResultList())low(p,"order:"+o.getId());
  }
 }
 public void low(Product p,String source){if(p==null||p.getStock()==null||p.getStock()<0||p.getStock()>=5)return;
  write("stock:"+p.getId()+":"+source,"LOW_STOCK","USER","PRODUCT",p.getId(),p.getOrganization()==null?null:p.getOrganization().getId(),p.getName(),null,null,p.getStock(),null);}
 public void user(User u){write("user:"+u.getId(),"USER_REGISTERED","SYSTEM","USER",u.getId(),null,u.getUsername(),null,null,null,null);}
 public void company(Organization o){write("company:"+o.getId(),"COMPANY_CREATED","SYSTEM","COMPANY",o.getId(),null,o.getName(),null,null,null,null);}
}
