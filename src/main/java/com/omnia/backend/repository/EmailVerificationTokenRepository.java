package com.omnia.backend.repository;

import com.omnia.backend.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(
            String tokenHash
    );

    Optional<EmailVerificationToken> findByUserId(
            Long userId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from EmailVerificationToken verificationToken
            where verificationToken.user.id = :userId
            """)
    int deleteByUserId(
            @Param("userId") Long userId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from EmailVerificationToken verificationToken
            where verificationToken.expiresAt <= :now
               or verificationToken.used = true
            """)
    int deleteExpiredOrUsedTokens(
            @Param("now") LocalDateTime now
    );
}