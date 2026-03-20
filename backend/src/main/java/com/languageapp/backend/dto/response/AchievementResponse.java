package com.languageapp.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AchievementResponse {
    private UUID achievementId;
    private String name;
    private String description;
    private String iconUrl;
    @JsonProperty("isUnlocked")
    private boolean isUnlocked;
    private LocalDateTime achievedAt;
}