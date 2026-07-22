package com.omnia.backend.repository;

import com.omnia.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    @Query("""
            select case when count(user) > 0
                        then true
                        else false
                   end
            from User user
            where user.avatarFile.id = :avatarFileId
              and user.id <> :userId
            """)
    boolean existsByAvatarFileIdAndUserIdNot(
            @Param("avatarFileId") Long avatarFileId,
            @Param("userId") Long userId
    );
}