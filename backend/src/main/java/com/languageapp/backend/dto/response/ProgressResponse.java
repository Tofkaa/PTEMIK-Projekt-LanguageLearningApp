package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object representing a user's progress on a specific lesson.
 * Used for populating the user dashboard on the frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressResponse {
    private UUID lessonId;
    private String lessonTitle;
    private int highestScore;
    private boolean isCompleted;
    private Instant lastAttemptAt;
    private Instant completedAt;
}