package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@AllArgsConstructor
public class ChallengeDTO {
    private UUID challengeId;
    private UUID lessonId;
    private String challengerName;
    private String opponentName;
    private String lessonTitle;
    private String difficulty;
    private String status;

    @JsonProperty("isMyTurn")
    private boolean isMyTurn;

    private Instant expiresAt;
    private Instant startTime;
    private Instant completedAt;
    private String winnerName;
}