package com.languageapp.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/** One current deletion challenge per account; never stores the emailed secret. */
@Entity
@Table(name = "account_deletion_tokens", indexes = @Index(name = "idx_deletion_expiry_cleanup", columnList = "expires_at,user_id"))
@Getter
@Setter
public class AccountDeletionToken {
    @Id private UUID userId;
    @Column(length = 64) private String tokenHash;
    @Column(nullable = false, length = 128) private String sessionHash;
    @Column(nullable = false, length = 64) private String accountFingerprint;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private Instant lastRequestedAt;
    @Column(nullable = false) private Instant rateWindowStart;
    @Column(nullable = false) private int requestsInWindow;
}
