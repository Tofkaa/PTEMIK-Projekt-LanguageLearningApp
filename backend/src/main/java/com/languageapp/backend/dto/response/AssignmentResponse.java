package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object representing an assignment's metadata for the UI.
 * Used for both teacher management views and student assignment lists.
 */
@Data
@AllArgsConstructor
public class AssignmentResponse {
    private UUID assignmentId;
    private String title;
    private String description;
    private boolean isTest;
    private Integer timeLimitMinutes;
    private LocalDateTime availableFrom;
    private LocalDateTime availableUntil;
    private int exerciseCount;
    private boolean isRandomized;
    private boolean allowRetries;
    private boolean hasFeedback;
}