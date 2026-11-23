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
  for(long user:recipients(org,customer)){jdbc.update("INSERT INTO notification_inbox (recipient_id,event_key,kind,category,target_type,target_id,organization_id,label,status,amount,quantity,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE id=id",user,key,kind,category,type,id,org,label==null?"":label.substring(0,Math.min(200,label.length())),status,amount,quantity);
   jdbc.update("INSERT INTO push_outbox(notification_id,available_at) SELECT id,UTC_TIMESTAMP(6) FROM notification_inbox WHERE recipient_id=? AND event_key=? ON DUPLICATE KEY UPDATE notification_id=notification_id",user,key);
  }
 }
 /** The users an order notification for this company and customer is written for (the FK parents of those inserts). */
 public Set<Long> orderRecipients(Long org,Long customer){return recipients(org,customer);}
 public void order(Order o){
  if(o==null||o.getId()==null)return;Long org=o.getOrganization()==null?null:o.getOrganization().getId();
  String status=o.getStatus().name();write("order:"+o.getId()+":"+status,status.equals("PENDING")?"ORDER_CREATED":"ORDER_STATUS","USER","ORDER",o.getId(),org,o.getOrganizationName(),status,o.getTotalAmount(),null,o.getUser().getId());
  if(status.equals("PENDING")){
   // Read final product stock once per product, after every line item has been applied.
   for(Product p:em.createQuery("select p from Product p where p.id in (select i.productId from OrderItem i where i.order.id=:id)",Product.class).setParameter("id",o.getId()).getResultList())low(p,"order:"+o.getId());
  }
 }
 /** Cars and properties are single listings (stock 1 by design), so they never get a low-stock notice. */
 public void low(Product p,String source){if(p==null||p.getStock()==null||p.getStock()<0||p.getStock()>=5)return;
  if(com.omnia.backend.catalog.ListingRules.kindOf(p).allowsContact())return;
  write("stock:"+p.getId()+":"+source,"LOW_STOCK","USER","PRODUCT",p.getId(),p.getOrganization()==null?null:p.getOrganization().getId(),p.getName(),null,null,p.getStock(),null);}
 /** "Kontakto" / "Kërko vizitë": the seller's owners/admins (and platform admins), never the buyer. */
 public void inquiry(com.omnia.backend.entity.ProductInquiry i){
  if(i==null||i.getId()==null||i.getProduct()==null)return;Long org=i.getOrganization()==null?null:i.getOrganization().getId();
  write("inquiry:"+i.getId(),"INQUIRY_"+i.getType().name(),"USER","PRODUCT",i.getProduct().getId(),org,i.getProduct().getName(),i.getType().name(),null,null,null);}
 /** A buyer notice (saved-search alert, price drop) for one account; category BUYER, never sent to sellers or admins. */
 private boolean buyer(long user,String key,String kind,String type,long id,String label,java.math.BigDecimal amount,java.math.BigDecimal previous,Integer quantity){
  if(jdbc.queryForObject("SELECT COUNT(*) FROM users u WHERE u.id=? AND u.deleted_at IS NULL AND u.status='ACTIVE'",Long.class,user)==0)return false;
  // The driver reports a matched duplicate as 1 row, so check the event key first; the unique key still guards races.
  if(jdbc.queryForObject("SELECT COUNT(*) FROM notification_inbox WHERE recipient_id=? AND event_key=?",Long.class,user,key)>0)return false;
  int added=jdbc.update("INSERT INTO notification_inbox (recipient_id,event_key,kind,category,target_type,target_id,organization_id,label,status,amount,previous_amount,quantity,created_at) VALUES (?,?,?,'BUYER',?,?,NULL,?,NULL,?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE id=id",user,key,kind,type,id,label==null?"":label.substring(0,Math.min(200,label.length())),amount,previous,quantity);
  if(added!=1)return false;
  jdbc.update("INSERT INTO push_outbox(notification_id,available_at) SELECT id,UTC_TIMESTAMP(6) FROM notification_inbox WHERE recipient_id=? AND event_key=? ON DUPLICATE KEY UPDATE notification_id=notification_id",user,key);
  return true;
 }
 /** New products match a saved search: one notice per search and newest product, with the number of new matches. */
 public boolean savedSearch(long user,long searchId,String name,long newestProductId,int count){
  em.flush();return buyer(user,"search:"+searchId+":"+newestProductId,"SAVED_SEARCH","SEARCH",searchId,name,null,null,count);}
 /** A favourited product got cheaper: notify everyone who has it in favourites. Returns the number of notices written. */
 public int priceDrop(Product p,java.math.BigDecimal before,java.math.BigDecimal after){
  if(p==null||p.getId()==null||before==null||after==null||after.compareTo(before)>=0)return 0;
  em.flush();int written=0;String key="price:"+p.getId()+":"+before.stripTrailingZeros().toPlainString()+">"+after.stripTrailingZeros().toPlainString();
  for(long user:jdbc.queryForList("SELECT f.user_id FROM favorites f WHERE f.product_id=? ORDER BY f.user_id",Long.class,p.getId()))if(buyer(user,key,"PRICE_DROP","PRODUCT",p.getId(),p.getName(),after,before,null))written++;
  return written;}
 /**
  * A new chat message: one notice for the other side only (the buyer, or the company's active owners/admins),
  * never the sender. While a recipient still has an unread notice for this chat, no new notice or push is added.
  */
 public void chat(long conversationId,long messageId,long organizationId,String productName,boolean fromBuyer,long buyerId,long senderId){
  em.flush();
  List<Long> users=fromBuyer
   ?jdbc.queryForList("SELECT u.id FROM users u JOIN organization_members m ON m.user_id=u.id JOIN organizations g ON g.id=m.organization_id WHERE m.organization_id=? AND m.status='ACTIVE' AND m.membership_role IN ('OWNER','ADMIN') AND g.status='ACTIVE' AND u.deleted_at IS NULL AND u.status='ACTIVE' AND u.id<>?",Long.class,organizationId,senderId)
   :jdbc.queryForList("SELECT u.id FROM users u WHERE u.id=? AND u.id<>? AND u.deleted_at IS NULL AND u.status='ACTIVE'",Long.class,buyerId,senderId);
  String label=productName==null?"":productName.substring(0,Math.min(200,productName.length()));String key="chat:"+messageId;
  for(long user:new TreeSet<>(users)){
   int added=jdbc.update("INSERT INTO notification_inbox (recipient_id,event_key,kind,category,target_type,target_id,organization_id,label,status,created_at) SELECT ?,?,'CHAT_MESSAGE','USER','CHAT',?,?,?,?,UTC_TIMESTAMP(6) FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM notification_inbox x WHERE x.recipient_id=? AND x.target_type='CHAT' AND x.target_id=? AND x.read_at IS NULL)",user,key,conversationId,organizationId,label,fromBuyer?"BUYER":"SELLER",user,conversationId);
   if(added>0)jdbc.update("INSERT INTO push_outbox(notification_id,available_at) SELECT id,UTC_TIMESTAMP(6) FROM notification_inbox WHERE recipient_id=? AND event_key=? ON DUPLICATE KEY UPDATE notification_id=notification_id",user,key);
  }
 }
 public void user(User u){write("user:"+u.getId(),"USER_REGISTERED","SYSTEM","USER",u.getId(),null,u.getUsername(),null,null,null,null);}
 /** "Shitës i verifikuar" decision, for the company's owners/admins (and platform admins). */
 public void verification(OrganizationVerification v){
  if(v==null||v.getId()==null||v.getOrganization()==null)return;Organization o=v.getOrganization();
  write("verification:"+v.getId()+":"+v.getStatus().name(),"VERIFICATION_"+v.getStatus().name(),"USER","COMPANY",o.getId(),o.getId(),o.getName(),v.getStatus().name(),null,null,null);}
 /** Moderation warning after "Raporto njoftimin": the company's owners/admins see which listing and why. */
 public void productWarning(ProductReport r){
  if(r==null||r.getId()==null||r.getProduct()==null)return;Long org=r.getOrganization()==null?null:r.getOrganization().getId();
  write("product-warning:"+r.getId(),"PRODUCT_WARNING","USER","PRODUCT",r.getProduct().getId(),org,r.getProduct().getName(),r.getReason().name(),null,null,null);}
 public void company(Organization o){write("company:"+o.getId(),"COMPANY_CREATED","SYSTEM","COMPANY",o.getId(),null,o.getName(),null,null,null,null);}
}
