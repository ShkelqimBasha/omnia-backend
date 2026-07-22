package com.omnia.backend.repository;

import com.omnia.backend.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(
            String tokenHash
    );

    Optional<PasswordResetToken> findByUserId(
            Long userId
    );

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            delete from PasswordResetToken token
            where token.user.id = :userId
            """)
    int deleteByUserId(
            @Param("userId") Long userId
    );

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            delete from PasswordResetToken token
            where token.expiresAt <= :now
               or token.used = true
            """)
    int deleteExpiredOrUsedTokens(
            @Param("now") LocalDateTime now
    );
}