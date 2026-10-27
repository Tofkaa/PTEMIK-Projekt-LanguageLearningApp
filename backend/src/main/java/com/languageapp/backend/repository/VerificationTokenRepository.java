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

    void deleteByUser_UserId(UUID userId);
}