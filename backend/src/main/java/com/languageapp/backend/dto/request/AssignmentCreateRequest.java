package com.languageapp.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import jakarta.validation.Valid;
import java.time.Instant;
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

    @JsonProperty("randomized")
    private boolean isRandomized;

    @JsonProperty("allowRetries")
    private boolean allowRetries;

    @JsonProperty("hasFeedback")
    private boolean hasFeedback;

    @JsonProperty("allowDictionary")
    private boolean allowDictionary;

    @JsonProperty("allowLateSubmission")
    private boolean allowLateSubmission;

    private Integer maxAttempts;

    private Instant availableFrom;
    private Instant availableUntil;
    private Integer timeLimitMinutes;
    private String generationMode;
    private Integer questionCount;
    private List<UUID> exerciseIds;
    @Valid
    private List<AssignmentInitialAccommodationRequest> accommodations;
}
