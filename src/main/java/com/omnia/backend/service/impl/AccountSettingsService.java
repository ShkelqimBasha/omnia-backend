package com.omnia.backend.service.impl;
import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.dto.request.*;
import com.omnia.backend.dto.response.UserSettingsResponse;
import com.omnia.backend.entity.*;
import com.omnia.backend.repository.*;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;

@Service
public class AccountSettingsService {
    private final CurrentUserService current;private final UserRepository users;
    private final UserSettingsRepository settings;private final PasswordEncoder encoder;
    private final RefreshTokenService refresh;private final PasswordResetTokenRepository resets;
    private final EntityManager em;
    public AccountSettingsService(CurrentUserService current,UserRepository users,UserSettingsRepository settings,
        PasswordEncoder encoder,RefreshTokenService refresh,PasswordResetTokenRepository resets,EntityManager em){
        this.current=current;this.users=users;this.settings=settings;this.encoder=encoder;this.refresh=refresh;this.resets=resets;this.em=em;
    }
    @Transactional(readOnly=true) public UserSettingsResponse get(){User user=current.requireCurrentUser();
        return settings.findById(user.getId()).map(this::response).orElseGet(()->new UserSettingsResponse(false,
            (clean(user.getFirstName())+" "+clean(user.getLastName())).trim(),clean(user.getPhone()),"","system","sq","EUR",true,-1));}
    @Transactional public UserSettingsResponse save(UserSettingsRequest r){
        User user=current.requireCurrentUser();em.lock(user,LockModeType.PESSIMISTIC_WRITE);
        UserSettings row=settings.findById(user.getId()).orElse(null);
        long revision=row==null?-1:row.getRevision();
        if(r.getRevision()==null||r.getRevision()!=revision)throw new ResourceAlreadyExistsException("Settings changed. Reload before saving.");
        if(row==null)row=UserSettings.builder().userId(user.getId()).build();
        row.setFullName(clean(r.getFullName()));row.setPhone(clean(r.getPhone()));row.setAddress(clean(r.getAddress()));
        row.setTheme(r.getTheme());row.setLanguage(r.getLanguage());row.setCurrency(r.getCurrency());row.setNotifications(Boolean.TRUE.equals(r.getNotifications()));
        return response(settings.saveAndFlush(row));
    }
    @Transactional public void password(ChangeCurrentPasswordRequest r){
        User user=current.requireCurrentUser();em.refresh(user,LockModeType.PESSIMISTIC_WRITE);
        String next=r.getNewPassword();
        if(next==null||next.isBlank()||next.length()<8||next.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("New password must have at least 8 characters and at most 72 UTF-8 bytes");
        if(r.getCurrentPassword()==null||!encoder.matches(r.getCurrentPassword(),user.getPasswordHash()))
            throw new IllegalArgumentException("Current password is incorrect");
        if(encoder.matches(next,user.getPasswordHash()))throw new IllegalArgumentException("New password must differ from current password");
        user.setPasswordHash(encoder.encode(next));users.saveAndFlush(user);
        refresh.revokeAllUserTokens(user.getId());resets.deleteByUserId(user.getId());
    }
    private UserSettingsResponse response(UserSettings r){return new UserSettingsResponse(true,r.getFullName(),r.getPhone(),r.getAddress(),r.getTheme(),r.getLanguage(),r.getCurrency(),r.isNotifications(),r.getRevision());}
    private static String clean(String s){return s==null?"":s.trim();}
}
