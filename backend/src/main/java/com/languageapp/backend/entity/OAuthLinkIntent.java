package com.languageapp.backend.entity;

import com.languageapp.backend.enums.AuthProvider;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @Entity @Table(name="oauth_link_intents")
public class OAuthLinkIntent {
    @Id private UUID userId;
    @Column(nullable=false,unique=true) private UUID requestId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private AuthProvider provider;
    @Column(nullable=false) private UUID sessionId;
    @Column(nullable=false,length=64) private String sessionHash;
    @Column(nullable=false,length=64) private String fingerprint;
    @Column(length=64) private String codeHash;
    @Column(nullable=false) private int failedAttempts;
    @Column(length=64,unique=true) private String authorizationHash;
    @Column(length=64) private String stateHash;
    @Column(length=64,unique=true) private String confirmationHash;
    @Column(length=255) private String providerSubject;
    @Column(length=254) private String providerEmail;
    @Column(nullable=false) private Instant expiresAt;
    private Instant confirmedAt;
    private Instant lastRequestedAt;
    private Instant rateWindowStart;
    @Column(nullable=false) private int requestsInWindow;
}
