package com.omnia.backend.service.impl;
import com.google.firebase.messaging.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import org.slf4j.*;

@Component
@EnableScheduling
public class PushDispatcher {
 private final JdbcTemplate jdbc;private final FirebasePushSender sender;private final PushPreferences prefs;private final TransactionTemplate tx;
 private static final Logger LOG=LoggerFactory.getLogger(PushDispatcher.class);
 public PushDispatcher(JdbcTemplate jdbc,FirebasePushSender sender,PushPreferences prefs,PlatformTransactionManager transactions){this.jdbc=jdbc;this.sender=sender;this.prefs=prefs;this.tx=new TransactionTemplate(transactions);}
 @Scheduled(fixedDelayString="${omnia.push.poll-ms:5000}",initialDelay=5000)
 public void dispatch(){if(!sender.ready())return;try{
  List<Long> ids=tx.execute(status->{var selected=jdbc.queryForList("SELECT notification_id FROM push_outbox WHERE state='PENDING' AND available_at<=UTC_TIMESTAMP(6) AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(6)) ORDER BY notification_id LIMIT 10 FOR UPDATE SKIP LOCKED",Long.class);for(long id:selected)jdbc.update("UPDATE push_outbox SET lease_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 10 MINUTE),attempts=attempts+1 WHERE notification_id=?",id);return selected;});
  if(ids!=null)for(long id:ids)deliver(id);
 }catch(Exception e){LOG.warn("Push queue temporarily unavailable ({})",e.getClass().getSimpleName());}}
 private void deliver(long id){try{
  var rows=jdbc.queryForList("SELECT n.*,u.role_id,r.name AS role_name,o.user_id AS buyer_id FROM notification_inbox n JOIN users u ON u.id=n.recipient_id JOIN roles r ON r.id=u.role_id LEFT JOIN orders o ON n.target_type='ORDER' AND o.id=n.target_id WHERE n.id=? AND n.read_at IS NULL AND n.created_at>=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 15 MINUTE) AND u.deleted_at IS NULL AND u.status='ACTIVE'",id);
  if(rows.isEmpty()){done(id);return;}var notice=rows.get(0);long user=((Number)notice.get("recipient_id")).longValue();String role=String.valueOf(notice.get("role_name"));boolean admin=role.equals("ADMIN")||role.equals("SUPER_ADMIN");
  List<Object> args=new ArrayList<>(List.of(user,user,admin?1:0,user,admin?1:0,user,admin?1:0,admin?1:0));args.add(id);
  if(jdbc.queryForObject("SELECT COUNT(*) FROM notification_inbox n WHERE "+NotificationInbox.VISIBLE+" AND n.id=?",Long.class,args.toArray())==0){done(id);return;}
  var prefRows=jdbc.queryForList("SELECT user_id FROM push_preferences WHERE user_id=?",user);if(prefRows.isEmpty()){done(id);return;}var p=prefs.values(user);String kind=String.valueOf(notice.get("kind"));
  boolean own=notice.get("buyer_id")!=null&&((Number)notice.get("buyer_id")).longValue()==user;
  boolean allowed=switch(kind){case "ORDER_CREATED" -> own?p.orders():p.adminOrders();case "ORDER_STATUS" -> own?p.orders():p.adminOrders();case "LOW_STOCK" -> p.stock();case "USER_REGISTERED","COMPANY_CREATED" -> role.equals("SUPER_ADMIN")&&p.audit();default -> false;};
  if(!p.enabled()||!allowed||PushRules.quiet(p.quiet(),p.quietStart(),p.quietEnd(),p.timeZone(),Instant.now())){done(id);return;}
  boolean retry=false;var devices=jdbc.queryForList("SELECT * FROM push_devices WHERE user_id=? AND enabled=TRUE AND last_seen>=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 45 DAY)",user);
  for(var d:devices){String installation=String.valueOf(d.get("installation_id"));long version=((Number)d.get("binding_version")).longValue();
   if(jdbc.queryForObject("SELECT COUNT(*) FROM push_deliveries WHERE notification_id=? AND installation_id=? AND binding_version=?",Long.class,id,installation,version)>0)continue;
   // No buyer, product, email or company details cross FCM. The tap uses the authenticated inbox API.
   Map<String,String> data=Map.of("noticeId",String.valueOf(id),"kind",kind,"tag",String.valueOf(d.get("binding_tag")),"version",String.valueOf(version),"expires",String.valueOf(Instant.now().plusSeconds(90).getEpochSecond()));
   try{sender.send(String.valueOf(d.get("token")),data);jdbc.update("INSERT INTO push_deliveries(notification_id,installation_id,binding_version) VALUES(?,?,?) ON DUPLICATE KEY UPDATE notification_id=notification_id",id,installation,version);}
   catch(FirebaseMessagingException failed){if(failed.getMessagingErrorCode()==MessagingErrorCode.UNREGISTERED)jdbc.update("UPDATE push_devices SET enabled=FALSE,token=NULL,token_hash=NULL WHERE installation_id=? AND binding_version=?",installation,version);else retry=true;}
  }
  if(retry)later(id);else done(id);
 }catch(Exception e){LOG.warn("Push delivery deferred for notice {} ({})",id,e.getClass().getSimpleName());later(id);}}
 private void done(long id){jdbc.update("UPDATE push_outbox SET state='DONE',lease_until=NULL WHERE notification_id=?",id);}
 private void later(long id){jdbc.update("UPDATE push_outbox SET state=CASE WHEN attempts>=6 THEN 'FAILED' ELSE 'PENDING' END,lease_until=NULL,available_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 30 SECOND) WHERE notification_id=?",id);}
}
