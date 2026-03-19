package com.languageapp.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * DTO for accepting a single exercise submission from a student.
 */
@Data
public class ExerciseSubmission {

    @NotNull(message = "exerciseId must not be empty!")
    private UUID exerciseId;

    @NotNull(message = "The answer can not be empty!")
    private Object answer;

    @JsonProperty("isRetry")
    private boolean isRetry;
}