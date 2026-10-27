package com.languageapp.backend.repository;

import com.languageapp.backend.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    Optional<VerificationToken> findByToken(String token);

    @Query("select t.user.userId from VerificationToken t where t.token = :token")
    Optional<UUID> findUserIdByToken(@Param("token") String token);

    @Query("select max(t.createdAt) from VerificationToken t where t.user.userId=:userId")
    Optional<java.time.Instant> lastCreatedAt(UUID userId);
    long countByUser_UserIdAndCreatedAtAfter(UUID userId, java.time.Instant since);
    @org.springframework.data.jpa.repository.Modifying
    @Query("update VerificationToken t set t.expiryDate=:now where t.user.userId=:userId and t.expiryDate>:now")
    void expirePrevious(UUID userId, java.time.Instant now);
    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from VerificationToken t where t.user.userId=:userId and t.createdAt<:before")
    void pruneOld(UUID userId, java.time.Instant before);
    void deleteByUser_UserId(UUID userId);
}