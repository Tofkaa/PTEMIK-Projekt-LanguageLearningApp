package com.languageapp.backend.dto.request;

import lombok.Data;
import java.util.UUID;

@Data
public class ChallengeCreateRequest {
    private UUID opponentId;
    private UUID lessonId;
    private int expiresInDays;
}