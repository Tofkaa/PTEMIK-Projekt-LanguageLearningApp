package com.languageapp.backend.repository;

import com.languageapp.backend.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByToken(String token);
    @Query("select t.user.userId from PasswordResetToken t where t.token = :token")
    Optional<UUID> findUserIdByToken(@Param("token") String token);
    void deleteByUser_UserId(UUID userId);
}
