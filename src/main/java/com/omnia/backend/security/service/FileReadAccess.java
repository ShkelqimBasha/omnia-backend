package com.omnia.backend.security.service;

import com.omnia.backend.entity.User;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may read an uploaded file once signed in (guests get only public catalog media).
 * Allowed: platform admins, the uploader, any product photo, any company logo, and an
 * avatar to its owner or to people in the same company. Everything else (other people's
 * avatars, files never attached) answers 404, so ids cannot be walked.
 */
@Component
public class FileReadAccess {

    private final EntityManager em;
    private final CurrentUserService currentUserService;

    public FileReadAccess(EntityManager em, CurrentUserService currentUserService) {
        this.em = em;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public boolean canRead(Long fileId) {
        if (fileId == null || fileId <= 0) return false;
        User me = currentUserService.findCurrentUser().orElse(null);
        if (me == null || me.getId() == null) return false;
        if (currentUserService.hasPlatformAdminAccess(me)) return true;

        if (exists("select count(f) from UploadedFile f where f.id = :id and f.uploadedBy.id = :me", fileId, me.getId())
                || exists("select count(i) from ProductImage i where i.uploadedFile.id = :id", fileId, null)
                || exists("select count(o) from Organization o where o.logoFile.id = :id", fileId, null)) {
            return true;
        }
        // An avatar: its owner, or someone active in the same company.
        return exists("""
                select count(u) from User u
                where u.avatarFile.id = :id
                  and (u.id = :me or exists (
                        select 1 from OrganizationMember mine, OrganizationMember theirs
                        where mine.user.id = :me and theirs.user.id = u.id
                          and mine.organization.id = theirs.organization.id
                          and mine.status = com.omnia.backend.enums.OrganizationMemberStatus.ACTIVE
                          and theirs.status = com.omnia.backend.enums.OrganizationMemberStatus.ACTIVE))
                """, fileId, me.getId());
    }

    private boolean exists(String jpql, Long fileId, Long me) {
        var query = em.createQuery(jpql, Long.class).setParameter("id", fileId);
        if (me != null) query.setParameter("me", me);
        Long count = query.getSingleResult();
        return count != null && count > 0;
    }
}
