package com.omnia.backend.service.impl;
import com.omnia.backend.integration.AbstractIntegrationTest;
import com.omnia.backend.entity.User;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.Order;
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.TestTransaction;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Transactional
class PushIntegrationTest extends AbstractIntegrationTest {
 @Autowired JdbcTemplate jdbc;@Autowired EntityManager em;@Autowired PlatformTransactionManager transactions;@Autowired NotificationWriter writer;
 private CurrentUserService current;private FirebasePushSender sender;private PushPreferences prefs;private PushDevices devices;private User buyer,other,admin;private long notice;
 private Role role(String name){var list=em.createQuery("select r from Role r where r.name=:n",Role.class).setParameter("n",name).getResultList();if(!list.isEmpty())return list.get(0);Role r=Role.builder().name(name).build();em.persist(r);return r;}
 private User user(String role){String id="p29"+UUID.randomUUID().toString().replace("-","");User u=User.builder().role(role(role)).username(id).email(id+"@example.test").firstName("Private buyer").passwordHash("unused").emailVerified(true).build();em.persist(u);return u;}
 @BeforeEach void setup(){jdbc.update("UPDATE push_outbox SET state='DONE' WHERE state='PENDING'");current=mock(CurrentUserService.class);sender=mock(FirebasePushSender.class);prefs=new PushPreferences(jdbc,current,sender);devices=new PushDevices(jdbc,prefs);buyer=user("USER");other=user("USER");admin=user("SUPER_ADMIN");Order order=Order.builder().user(buyer).status(OrderStatus.PENDING).totalAmount(BigDecimal.TEN).build();em.persist(order);em.flush();as(buyer);
  String key="test:"+UUID.randomUUID();jdbc.update("INSERT INTO notification_inbox(recipient_id,event_key,kind,category,target_type,target_id,label,created_at) VALUES(?,?,'ORDER_CREATED','USER','ORDER',?,'Private order',UTC_TIMESTAMP(6))",buyer.getId(),key,order.getId());notice=jdbc.queryForObject("SELECT id FROM notification_inbox WHERE recipient_id=? AND event_key=?",Long.class,buyer.getId(),key);jdbc.update("INSERT INTO push_outbox(notification_id,available_at) VALUES(?,UTC_TIMESTAMP(6))",notice);
 }
 private void as(User u){when(current.requireCurrentUser()).thenReturn(u);when(current.hasPlatformAdminAccess(u)).thenReturn(u.getRole().getName().equals("SUPER_ADMIN"));}
 private PushPreferences.Values enabled(boolean quiet,int start,int end,long revision){return new PushPreferences.Values(true,true,true,true,true,true,true,true,true,quiet,start,end,"UTC",true,true,revision);}
 private PushDevices.Registration device(String id,String secret,String token,long version,String tag){return new PushDevices.Registration(id,secret,token,version,tag,true);}
 @Test void staleRegistrationCannotRebindAnInstallationToPreviousAccount(){String id=UUID.randomUUID().toString(),secret="a".repeat(64),a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();devices.register(device(id,secret,"token",1,a));as(other);devices.register(device(id,secret,"token",3,b));as(buyer);assertThrows(ResourceAlreadyExistsException.class,()->devices.register(device(id,secret,"token",2,a)));assertThrows(AccessDeniedException.class,()->devices.register(device(id,"b".repeat(64),"token",4,a)));assertEquals(other.getId(),jdbc.queryForObject("SELECT user_id FROM push_devices WHERE installation_id=?",Long.class,id));}
 @Test void preferencesMaskPrivilegedCategoriesAndRejectStaleRevisions(){var value=prefs.save(enabled(false,1320,480,0));assertFalse(value.preferences().adminOrders());assertFalse(value.preferences().stock());assertFalse(value.preferences().audit());assertFalse(value.preferences().reports());assertThrows(ResourceAlreadyExistsException.class,()->prefs.save(enabled(false,1320,480,0)));assertThrows(IllegalArgumentException.class,()->prefs.save(enabled(true,500,500,1)));}
 @Test void successfulDeliveryIsDeduplicatedAndContainsNoPersonalDetails()throws Exception {when(sender.ready()).thenReturn(true);prefs.save(enabled(false,1320,480,0));devices.register(device(UUID.randomUUID().toString(),"a".repeat(64),"opaque-token",1,UUID.randomUUID().toString()));PushDispatcher dispatcher=new PushDispatcher(jdbc,sender,prefs,transactions);dispatcher.dispatch();dispatcher.dispatch();verify(sender,times(1)).send(eq("opaque-token"),argThat(data->data.keySet().equals(Set.of("noticeId","kind","tag","version","expires"))&&!data.toString().contains("Private")));assertEquals("DONE",jdbc.queryForObject("SELECT state FROM push_outbox WHERE notification_id=?",String.class,notice));}
 @Test void optOutAndAlreadyReadRowsNeverSend()throws Exception {when(sender.ready()).thenReturn(true);prefs.get();devices.register(device(UUID.randomUUID().toString(),"a".repeat(64),"opaque-token",1,UUID.randomUUID().toString()));new PushDispatcher(jdbc,sender,prefs,transactions).dispatch();verify(sender,never()).send(anyString(),anyMap());jdbc.update("UPDATE push_outbox SET state='PENDING' WHERE notification_id=?",notice);jdbc.update("UPDATE notification_inbox SET read_at=UTC_TIMESTAMP(6) WHERE id=?",notice);prefs.save(enabled(false,1320,480,0));new PushDispatcher(jdbc,sender,prefs,transactions).dispatch();verify(sender,never()).send(anyString(),anyMap());}
 @Test void quietHoursSuppressDelivery()throws Exception {when(sender.ready()).thenReturn(true);int now=ZonedDateTime.now(ZoneOffset.UTC).getHour()*60+ZonedDateTime.now(ZoneOffset.UTC).getMinute();prefs.save(enabled(true,(now+1435)%1440,(now+5)%1440,0));devices.register(device(UUID.randomUUID().toString(),"a".repeat(64),"opaque-token",1,UUID.randomUUID().toString()));new PushDispatcher(jdbc,sender,prefs,transactions).dispatch();verify(sender,never()).send(anyString(),anyMap());}
 @Test void outboxAndNotificationRollBackWithBusinessEvent(){writer.user(other);Long count=jdbc.queryForObject("SELECT COUNT(*) FROM push_outbox b JOIN notification_inbox n ON n.id=b.notification_id WHERE n.recipient_id=? AND n.target_type='USER' AND n.target_id=?",Long.class,admin.getId(),other.getId());assertEquals(1L,count);long user=admin.getId();TestTransaction.flagForRollback();TestTransaction.end();assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM notification_inbox WHERE recipient_id=?",Long.class,user));}
}
