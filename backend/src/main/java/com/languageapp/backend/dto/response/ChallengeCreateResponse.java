package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ChallengeCreateResponse {
    private UUID challengeId;
    private String status;
    private LocalDateTime expiresAt;
}