package com.omnia.backend.service.impl;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.mapper.ProductMapper;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import jakarta.persistence.EntityManager;
import java.sql.*;
import java.time.*;
import java.util.*;

@Service
public class NotificationInbox {
 private final JdbcTemplate jdbc;private final CurrentUserService current;private final EntityManager em;
 public NotificationInbox(JdbcTemplate jdbc,CurrentUserService current,EntityManager em){this.jdbc=jdbc;this.current=current;this.em=em;}
 // Identical current-access predicate for list, read-all and open. No client-supplied actor or company.
 static final String MANAGER="EXISTS (SELECT 1 FROM organization_members m JOIN organizations g ON g.id=m.organization_id WHERE m.user_id=? AND m.organization_id=n.organization_id AND m.status='ACTIVE' AND m.membership_role IN ('OWNER','ADMIN') AND g.status='ACTIVE')";
 static final String VISIBLE="n.recipient_id=? AND ((n.target_type='ORDER' AND EXISTS (SELECT 1 FROM orders o WHERE o.id=n.target_id AND (o.user_id=? OR ?=1 OR (o.organization_id=n.organization_id AND "+MANAGER+")))) OR (n.target_type='PRODUCT' AND EXISTS (SELECT 1 FROM products p WHERE p.id=n.target_id AND (p.organization_id=n.organization_id OR (p.organization_id IS NULL AND n.organization_id IS NULL)) AND (?=1 OR "+MANAGER+"))) OR (n.target_type='USER' AND ?=1 AND EXISTS (SELECT 1 FROM users t WHERE t.id=n.target_id AND t.deleted_at IS NULL)) OR (n.target_type='COMPANY' AND ?=1 AND EXISTS (SELECT 1 FROM organizations g WHERE g.id=n.target_id)))";
 public record Notice(long id,String kind,String category,String targetType,long targetId,Long organizationId,String label,String status,java.math.BigDecimal amount,Integer quantity,String createdAt,boolean read){}
 public record Page(List<Notice> items,long throughId,long unread,Long nextBefore){}
 private static final RowMapper<Notice> MAPPER=(r,i)->new Notice(r.getLong("id"),r.getString("kind"),r.getString("category"),r.getString("target_type"),r.getLong("target_id"),r.getObject("organization_id",Long.class),r.getString("label"),r.getString("status"),r.getBigDecimal("amount"),r.getObject("quantity",Integer.class),r.getTimestamp("created_at").toLocalDateTime().toInstant(ZoneOffset.UTC).toString(),r.getTimestamp("read_at")!=null);
 private List<Object> actor(){User u=current.requireCurrentUser();if(u.getStatus()!=UserStatus.ACTIVE)throw new AccessDeniedException("Active account required");long id=u.getId();int admin=current.hasPlatformAdminAccess(u)?1:0;return new ArrayList<>(List.of(id,id,admin,id,admin,id,admin,admin));}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Page list(String filter,long before){
  if(!Set.of("all","unread","system").contains(filter)||before<0)throw new IllegalArgumentException("Invalid notification filter or cursor");
  var args=actor();String extra=filter.equals("unread")?" AND n.read_at IS NULL":filter.equals("system")?" AND n.category='SYSTEM'":"";
  Long high=jdbc.queryForObject("SELECT COALESCE(MAX(n.id),0) FROM notification_inbox n WHERE "+VISIBLE,Long.class,args.toArray());
  Long unread=jdbc.queryForObject("SELECT COUNT(*) FROM notification_inbox n WHERE "+VISIBLE+" AND n.read_at IS NULL",Long.class,args.toArray());
  if(before>0){extra+=" AND n.id<?";args.add(before);}
  List<Notice> rows=jdbc.query("SELECT n.* FROM notification_inbox n WHERE "+VISIBLE+extra+" ORDER BY n.id DESC LIMIT 21",MAPPER,args.toArray());
  boolean more=rows.size()>20;if(more)rows=new ArrayList<>(rows.subList(0,20));return new Page(List.copyOf(rows),high==null?0:high,unread==null?0:unread,more?rows.get(rows.size()-1).id():null);
 }
 @Transactional public void readAll(long throughId){if(throughId<=0)throw new IllegalArgumentException("Invalid notification watermark");var args=actor();args.add(throughId);
  jdbc.update("UPDATE notification_inbox n SET read_at=COALESCE(read_at,UTC_TIMESTAMP(6)) WHERE "+VISIBLE+" AND n.id<=?",args.toArray());}
 @Transactional public Map<String,Object> open(long id){
  var args=actor();args.add(id);List<Notice> rows=jdbc.query("SELECT n.* FROM notification_inbox n WHERE "+VISIBLE+" AND n.id=?",MAPPER,args.toArray());
  if(rows.isEmpty())throw new ResourceNotFoundException("Notification or target unavailable");Notice n=rows.get(0);Map<String,Object> detail=new LinkedHashMap<>();detail.put("notification",n);
  // Only explicit safe fields; never serialize a User/Order entity or password/token relationships.
  switch(n.targetType()){
   case "ORDER" -> {Order o=em.find(Order.class,n.targetId());if(o==null)throw new ResourceNotFoundException("Order unavailable");detail.put("name","#"+o.getId());detail.put("status",o.getStatus().name());detail.put("amount",o.getTotalAmount());detail.put("organization",o.getOrganizationName()==null?"":o.getOrganizationName());
    detail.put("items",jdbc.queryForList("SELECT product_name AS name,quantity,subtotal FROM order_items WHERE order_id=? ORDER BY id",o.getId()));}
   case "PRODUCT" -> {Product p=em.find(Product.class,n.targetId());if(p==null)throw new ResourceNotFoundException("Product unavailable");Map<String,Object> product=new LinkedHashMap<>();product.put("id",p.getId());product.put("name",p.getName());product.put("description",safe(p.getDescription()));product.put("price",p.getPrice());product.put("discount",p.getDiscountPrice());product.put("categoryName",p.getCategory().getName());product.put("categoryId",p.getCategory().getId());product.put("brand",safe(p.getBrand()));product.put("status",p.getStatus().name());product.put("image",safe(ProductMapper.primaryImageUrl(p)));product.put("stock",p.getStock());detail.put("product",product);}
   case "USER" -> {User u=em.find(User.class,n.targetId());if(u==null)throw new ResourceNotFoundException("User unavailable");detail.put("name",safe(u.getFirstName())+" "+safe(u.getLastName()));detail.put("username",u.getUsername());detail.put("email",u.getEmail());detail.put("status",u.getStatus().name());}
   case "COMPANY" -> {Organization o=em.find(Organization.class,n.targetId());if(o==null)throw new ResourceNotFoundException("Company unavailable");detail.put("name",o.getName());detail.put("slug",o.getSlug());detail.put("description",safe(o.getDescription()));detail.put("status",o.getStatus().name());}
   default -> throw new ResourceNotFoundException("Unsupported target");
  }
  jdbc.update("UPDATE notification_inbox SET read_at=COALESCE(read_at,UTC_TIMESTAMP(6)) WHERE id=? AND recipient_id=?",id,current.requireCurrentUser().getId());return detail;
 }
 private static String safe(String s){return s==null?"":s;}
}
