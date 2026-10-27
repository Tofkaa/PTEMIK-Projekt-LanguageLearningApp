package com.languageapp.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_change_tokens")
@Data
@NoArgsConstructor
public class EmailChangeToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 6)
    private String otpCode;

    @Column(nullable = false)
    private String newEmail;

    @Column(nullable = false)
    private Instant expiryDate;

    // The default also initializes already-existing challenges when Hibernate adds this column.
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int failedAttempts = 0;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
}