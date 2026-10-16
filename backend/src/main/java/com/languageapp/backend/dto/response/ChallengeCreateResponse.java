package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ChallengeCreateResponse {
    private UUID challengeId;
    private String status;
    private Instant expiresAt;
}