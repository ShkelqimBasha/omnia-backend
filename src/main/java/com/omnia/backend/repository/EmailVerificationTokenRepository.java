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

    Optional<EmailVerificationToken> findByToken(String token);

    Optional<EmailVerificationToken> findByUserId(Long userId);

    @Modifying
    @Query("""
            delete from EmailVerificationToken token
            where token.user.id = :userId
            """)
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("""
            delete from EmailVerificationToken token
            where token.expiresAt < :now
            """)
    int deleteExpiredTokens(@Param("now") LocalDateTime now);
}