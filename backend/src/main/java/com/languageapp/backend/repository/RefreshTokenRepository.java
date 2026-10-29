package com.languageapp.backend.repository;

import com.languageapp.backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.user.userId = :userId")
    void deleteByUserUserId(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.tokenHash = :hash")
    int revokeByTokenHash(@Param("hash") String hash);

    @Query("select count(t) > 0 from RefreshToken t where t.refreshTokenId = :sid " +
            "and t.user.email = :email and t.user.isActive = true and t.user.role <> com.languageapp.backend.enums.Role.DELETED and t.expiryDate > :now")
    boolean isSessionActive(@Param("sid") UUID sessionId, @Param("email") String email, @Param("now") Instant now);
}
