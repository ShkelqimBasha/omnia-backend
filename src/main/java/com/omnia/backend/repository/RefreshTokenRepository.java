package com.omnia.backend.repository;

import com.omnia.backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(
            String tokenHash
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken refreshToken
            set refreshToken.revoked = true
            where refreshToken.user.id = :userId
              and refreshToken.revoked = false
            """)
    int revokeAllActiveTokensByUserId(
            @Param("userId") Long userId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from RefreshToken refreshToken
            where refreshToken.expiresAt <= :now
               or refreshToken.revoked = true
            """)
    int deleteExpiredOrRevokedTokens(
            @Param("now") LocalDateTime now
    );
}