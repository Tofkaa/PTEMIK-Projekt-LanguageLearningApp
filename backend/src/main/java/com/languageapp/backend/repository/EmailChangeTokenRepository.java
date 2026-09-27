package com.languageapp.backend.repository;

import com.languageapp.backend.entity.EmailChangeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailChangeTokenRepository extends JpaRepository<EmailChangeToken, UUID> {
    Optional<EmailChangeToken> findByUser_UserId(UUID userId);
    void deleteByUser_UserId(UUID userId);
}