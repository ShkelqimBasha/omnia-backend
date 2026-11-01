package com.omnia.backend.service.impl;
import com.omnia.backend.integration.AbstractIntegrationTest;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.*;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test; import org.junit.jupiter.api.BeforeEach; import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Transactional
class NotificationInboxIntegrationTest extends AbstractIntegrationTest {
 @Autowired EntityManager em;@Autowired JdbcTemplate jdbc;@Autowired NotificationWriter writer;
 private CurrentUserService current;private NotificationInbox inbox;private User admin,buyer,other,manager;private Organization org;private Order order;private Product product;
 private Role role(String name){var rows=em.createQuery("select r from Role r where r.name=:n",Role.class).setParameter("n",name).getResultList();if(!rows.isEmpty())return rows.get(0);Role r=Role.builder().name(name).build();em.persist(r);return r;}
 private User user(String role){String id="n28"+UUID.randomUUID().toString().replace("-","");User u=User.builder().role(role(role)).username(id).email(id+"@example.test").firstName(id).passwordHash("unused").emailVerified(true).status(UserStatus.ACTIVE).build();em.persist(u);return u;}
 @BeforeEach void setup(){
  current=mock(CurrentUserService.class);inbox=new NotificationInbox(jdbc,current,em);
  admin=user("SUPER_ADMIN");buyer=user("USER");other=user("USER");manager=user("USER");
  org=Organization.builder().name("n28-"+UUID.randomUUID()).slug("n28-"+UUID.randomUUID()).status(OrganizationStatus.ACTIVE).build();em.persist(org);
  em.persist(OrganizationMember.builder().organization(org).user(manager).membershipRole(OrganizationMemberRole.ADMIN).status(OrganizationMemberStatus.ACTIVE).build());
  order=Order.builder().user(buyer).organization(org).organizationName(org.getName()).totalAmount(new BigDecimal("150.00")).status(OrderStatus.PENDING).build();em.persist(order);
  Category cat=Category.builder().name("Notification fixtures").status(CategoryStatus.ACTIVE).build();em.persist(cat);
  product=Product.builder().organization(org).category(cat).name("Actual product").price(BigDecimal.TEN).stock(2).status(ProductStatus.ACTIVE).build();em.persist(product);em.flush();
 }
 private void as(User u){when(current.requireCurrentUser()).thenReturn(u);when(current.hasPlatformAdminAccess(u)).thenReturn(u.getRole().getName().equals("ADMIN")||u.getRole().getName().equals("SUPER_ADMIN"));}
 private long find(String type){return inbox.list("all",0).items().stream().filter(n->n.targetType().equals(type)).findFirst().orElseThrow().id();}
 @Test void realEventsAreIdempotentAndBelongOnlyToTheirRecipients(){
  writer.order(order);writer.order(order);writer.low(product,"edit:1");writer.user(other);writer.company(org);
  as(buyer);assertEquals(1,inbox.list("all",0).items().size());assertEquals(1,inbox.list("unread",0).unread());assertTrue(inbox.list("system",0).items().isEmpty());long buyerNotice=find("ORDER");
  as(other);assertTrue(inbox.list("all",0).items().isEmpty());assertThrows(ResourceNotFoundException.class,()->inbox.open(buyerNotice));
  as(manager);assertEquals(2,inbox.list("all",0).items().size());assertTrue(inbox.list("system",0).items().isEmpty());
  as(admin);assertEquals(2,inbox.list("system",0).items().size());assertEquals(4,inbox.list("all",0).items().size());
 }
 @Test void revokedMembershipHidesRowsCountsAndDeniesOpeningOrBulkRead(){
  writer.order(order);writer.low(product,"edit:2");as(manager);long id=find("PRODUCT");
  jdbc.update("UPDATE organization_members SET status='INACTIVE' WHERE user_id=?",manager.getId());
  assertTrue(inbox.list("all",0).items().isEmpty());assertEquals(0,inbox.list("unread",0).unread());assertThrows(ResourceNotFoundException.class,()->inbox.open(id));inbox.readAll(Long.MAX_VALUE);
  assertNull(jdbc.queryForObject("SELECT read_at FROM notification_inbox WHERE id=?",java.sql.Timestamp.class,id));
  // The customer still owns their own order even when no company membership exists.
  as(buyer);assertEquals(1,inbox.list("all",0).items().size());
 }
 @Test void movedProductAndDeletedUserTargetsCannotLeakOldSnapshots(){
  writer.low(product,"edit:3");writer.user(other);as(admin);long userId=find("USER"),productId=find("PRODUCT");
  Organization next=Organization.builder().name("moved").slug("moved-"+UUID.randomUUID()).build();em.persist(next);product.setOrganization(next);other.setDeletedAt(java.time.LocalDateTime.now());em.flush();
  assertTrue(inbox.list("all",0).items().isEmpty());assertThrows(ResourceNotFoundException.class,()->inbox.open(productId));assertThrows(ResourceNotFoundException.class,()->inbox.open(userId));
 }
 @Test void watermarkDoesNotMarkArrivalsAndReadStateSurvivesANewServiceInstance(){
  writer.order(order);as(buyer);long first=inbox.list("all",0).throughId();order.setStatus(OrderStatus.CONFIRMED);writer.order(order);inbox.readAll(first);
  NotificationInbox reloaded=new NotificationInbox(jdbc,current,em);assertEquals(1,reloaded.list("unread",0).items().size());long latest=reloaded.list("unread",0).items().get(0).id();
  assertEquals("ORDER",((NotificationInbox.Notice)reloaded.open(latest).get("notification")).targetType());reloaded.open(latest);assertEquals(0,reloaded.list("unread",0).unread());
 }
 @Test void paginationAndFilterValidationUseBoundedStableIds(){
  for(int i=0;i<25;i++)jdbc.update("INSERT INTO notification_inbox(recipient_id,event_key,kind,category,target_type,target_id,label,created_at) VALUES(?,?,'ORDER_CREATED','USER','ORDER',?,'Actual order',UTC_TIMESTAMP(6))",buyer.getId(),"fixture:"+i,order.getId());
  as(buyer);var page=inbox.list("all",0);assertEquals(20,page.items().size());assertNotNull(page.nextBefore());var next=inbox.list("all",page.nextBefore());assertEquals(5,next.items().size());assertNull(next.nextBefore());
  Set<Long> ids=new HashSet<>();page.items().forEach(n->assertTrue(ids.add(n.id())));next.items().forEach(n->assertTrue(ids.add(n.id())));
  assertThrows(IllegalArgumentException.class,()->inbox.list("other",0));assertThrows(IllegalArgumentException.class,()->inbox.list("all",-1));assertThrows(IllegalArgumentException.class,()->inbox.readAll(0));
 }
 @Test void blockedActorIsRejectedBeforeReadingNotificationData(){writer.order(order);buyer.setStatus(UserStatus.BANNED);em.flush();as(buyer);assertThrows(AccessDeniedException.class,()->inbox.list("all",0));}
 @Test void lowStockThresholdIncludesZeroButNotHealthyInventory(){writer.low(product,"edit:4");product.setStock(0);writer.low(product,"edit:5");product.setStock(5);writer.low(product,"edit:6");as(manager);assertEquals(2,inbox.list("all",0).items().size());}
 @Test void inboxRowsRollBackWithTheBusinessTransaction(){writer.order(order);as(buyer);long recipient=buyer.getId();assertEquals(1,inbox.list("all",0).items().size());TestTransaction.flagForRollback();TestTransaction.end();assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM notification_inbox WHERE recipient_id=?",Long.class,recipient));}
 @Test void downgradedAdministratorCannotReadFormerAuditNotifications(){writer.user(other);as(admin);long id=find("USER");admin.setRole(role("USER"));em.flush();as(admin);assertTrue(inbox.list("system",0).items().isEmpty());assertThrows(ResourceNotFoundException.class,()->inbox.open(id));}
}
