package com.languageapp.backend.dto.request;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object for creating a new classroom assignment.
 * Encapsulates scheduling, test-specific constraints, and the selected exercises.
 */
@Data
public class AssignmentCreateRequest {
    private String title;
    private String description;

    // If true, the assignment functions as a graded test with stricter rules
    private boolean isTest;

    // Determines if the order of exercises should be shuffled for each student
    private boolean isRandomized;

    // Controls if students can retry failed exercises within the session
    private boolean allowRetries;

    // Time window for when the assignment becomes visible and accessible
    private LocalDateTime availableFrom;

    // Deadline for when the assignment can no longer be started
    private LocalDateTime availableUntil;

    // Allocated time in minutes once the student starts the session
    private Integer timeLimitMinutes;

    // List of specific exercise IDs selected by the teacher for this assignment
    private List<UUID> exerciseIds;
}