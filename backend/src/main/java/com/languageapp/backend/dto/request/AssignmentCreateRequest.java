package com.languageapp.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating a new classroom assignment.
 * Contains scheduling rules, test constraints, and the list of selected exercises.
 */
@Data
public class AssignmentCreateRequest {
    private String title;
    private String description;

    @JsonProperty("isTest")
    private boolean isTest;

    private boolean isRandomized;
    private boolean allowRetries;

    private LocalDateTime availableFrom;
    private LocalDateTime availableUntil;
    private Integer timeLimitMinutes;
    private List<UUID> exerciseIds;
}