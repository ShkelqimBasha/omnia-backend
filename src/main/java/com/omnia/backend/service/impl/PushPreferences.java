package com.omnia.backend.service.impl;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.security.service.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import java.util.*;

@Service
public class PushPreferences {
 private final JdbcTemplate jdbc;private final CurrentUserService current;private final FirebasePushSender sender;
 public PushPreferences(JdbcTemplate jdbc,CurrentUserService current,FirebasePushSender sender){this.jdbc=jdbc;this.current=current;this.sender=sender;}
 public record Values(boolean enabled,boolean orders,boolean payments,boolean offers,boolean favorites,boolean adminOrders,boolean stock,boolean audit,boolean reports,boolean quiet,int quietStart,int quietEnd,String timeZone,boolean sound,boolean vibration,long revision){}
 public record Response(Values preferences,boolean configured,boolean manager,boolean superAdmin){}
 User actor(){User u=current.requireCurrentUser();if(u.getStatus()!=UserStatus.ACTIVE)throw new AccessDeniedException("Active account required");return u;}
 boolean manager(User u){return current.hasPlatformAdminAccess(u)||Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM organization_members m JOIN organizations o ON o.id=m.organization_id WHERE m.user_id=? AND m.status='ACTIVE' AND m.membership_role IN ('OWNER','ADMIN') AND o.status='ACTIVE')",Boolean.class,u.getId()));}
 boolean superAdmin(User u){return u.getRole()!=null&&"SUPER_ADMIN".equals(u.getRole().getName());}
 void ensure(long id){jdbc.update("INSERT INTO push_preferences(user_id) VALUES(?) ON DUPLICATE KEY UPDATE user_id=user_id",id);}
 Values values(long id){return jdbc.queryForObject("SELECT * FROM push_preferences WHERE user_id=?",(r,i)->new Values(r.getBoolean("enabled"),r.getBoolean("orders_enabled"),r.getBoolean("payments_enabled"),r.getBoolean("offers_enabled"),r.getBoolean("favorites_enabled"),r.getBoolean("admin_orders_enabled"),r.getBoolean("stock_enabled"),r.getBoolean("audit_enabled"),r.getBoolean("reports_enabled"),r.getBoolean("quiet_enabled"),r.getInt("quiet_start"),r.getInt("quiet_end"),r.getString("time_zone"),r.getBoolean("sound_enabled"),r.getBoolean("vibration_enabled"),r.getLong("revision")),id);}
 @Transactional public Response get(){User u=actor();ensure(u.getId());return new Response(values(u.getId()),sender.ready(),manager(u),superAdmin(u));}
 @Transactional public Response save(Values v){if(v==null)throw new IllegalArgumentException("Preferences required");User u=actor();PushRules.validateTime(v.quietStart(),v.quietEnd(),v.timeZone());ensure(u.getId());
  // Privileged categories are masked according to the current database role, never a client flag.
  boolean m=manager(u),s=superAdmin(u);
  int count=jdbc.update("UPDATE push_preferences SET enabled=?,orders_enabled=?,payments_enabled=?,offers_enabled=?,favorites_enabled=?,admin_orders_enabled=?,stock_enabled=?,audit_enabled=?,reports_enabled=?,quiet_enabled=?,quiet_start=?,quiet_end=?,time_zone=?,sound_enabled=?,vibration_enabled=?,revision=revision+1 WHERE user_id=? AND revision=?",v.enabled(),v.orders(),v.payments(),v.offers(),v.favorites(),m&&v.adminOrders(),m&&v.stock(),s&&v.audit(),m&&v.reports(),v.quiet(),v.quietStart(),v.quietEnd(),v.timeZone(),v.sound(),v.vibration(),u.getId(),v.revision());
  if(count!=1)throw new ResourceAlreadyExistsException("Preferences changed. Reload before saving.");return new Response(values(u.getId()),sender.ready(),m,s);
 }
}
