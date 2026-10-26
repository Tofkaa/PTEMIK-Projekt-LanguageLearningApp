package com.languageapp.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name="invitation_links")
public class InvitationLink {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID invitationId;
    @Column(nullable=false, unique=true, length=100) private String scopeKey;
    @Column(nullable=false, length=12) private String kind;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="owner_id", nullable=false) private User owner;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="classroom_id") private Classroom classroom;
    // A reusable invitation identifier, never an authentication or authorization credential.
    @Column(unique=true, length=43) private String token;
    @Column(nullable=false) private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private Instant revokedAt;
}
