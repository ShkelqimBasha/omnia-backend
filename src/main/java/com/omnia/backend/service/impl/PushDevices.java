package com.omnia.backend.service.impl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import java.util.*;

@Service
public class PushDevices {
 private final JdbcTemplate jdbc;private final PushPreferences preferences;
 public PushDevices(JdbcTemplate jdbc,PushPreferences preferences){this.jdbc=jdbc;this.preferences=preferences;}
 public record Registration(String installationId,String secret,String token,long version,String tag,boolean enabled){}
 @Transactional public void register(Registration r){var u=preferences.actor();validate(r);String hash=PushRules.hash(r.secret());
  // Atomic insert + row lock serializes registration/account switches for one installation.
  jdbc.update("INSERT INTO push_devices(installation_id,secret_hash,binding_version,binding_tag,enabled,last_seen) VALUES(?,?,0,?,FALSE,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE installation_id=installation_id",r.installationId(),hash,r.tag());
  var rows=jdbc.queryForList("SELECT secret_hash,binding_version,user_id,binding_tag FROM push_devices WHERE installation_id=? FOR UPDATE",r.installationId());var row=rows.get(0);
  if(!PushRules.secretMatches(r.secret(),String.valueOf(row.get("secret_hash"))))throw new AccessDeniedException("Installation proof mismatch");
  long previous=((Number)row.get("binding_version")).longValue();Long owner=row.get("user_id")==null?null:((Number)row.get("user_id")).longValue();
  if(r.version()<previous||(r.version()==previous&&(!Objects.equals(owner,u.getId())||!Objects.equals(row.get("binding_tag"),r.tag()))))throw new ResourceAlreadyExistsException("Stale installation binding");
  if(r.enabled()&&jdbc.queryForObject("SELECT COUNT(*) FROM push_devices WHERE user_id=? AND enabled=TRUE AND installation_id<>? AND last_seen>=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 45 DAY)",Long.class,u.getId(),r.installationId())>=10)throw new IllegalArgumentException("Active device limit reached");
  String token=r.enabled()?r.token():null;String tokenHash=token==null?null:PushRules.hash(token);
  if(tokenHash!=null&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM push_devices WHERE token_hash=? AND installation_id<>?)",Boolean.class,tokenHash,r.installationId())))throw new ResourceAlreadyExistsException("Token already assigned to another installation");
  jdbc.update("UPDATE push_devices SET user_id=?,token=?,token_hash=?,binding_version=?,binding_tag=?,enabled=?,last_seen=UTC_TIMESTAMP(6) WHERE installation_id=?",u.getId(),token,tokenHash,r.version(),r.tag(),r.enabled(),r.installationId());
 }
 private void validate(Registration r){if(r==null||r.secret()==null||!r.secret().matches("[0-9a-f]{64}")||r.version()<1||r.token()==null||r.token().length()>4096||(r.enabled()&&r.token().isBlank()))throw new IllegalArgumentException("Invalid registration");try{if(!UUID.fromString(r.installationId()).toString().equals(r.installationId())||!UUID.fromString(r.tag()).toString().equals(r.tag()))throw new IllegalArgumentException();}catch(Exception e){throw new IllegalArgumentException("Invalid installation id");}}
}
