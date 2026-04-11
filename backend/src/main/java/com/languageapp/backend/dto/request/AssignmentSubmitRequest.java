package com.languageapp.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

/**
 * Request DTO for submitting completed assignment answers.
 * Instead of a score, it carries the raw exercise submissions to be graded server-side.
 */
@Data
public class AssignmentSubmitRequest {

    @NotEmpty(message = "The list of answers cannot be empty")
    @Valid
    private List<ExerciseSubmission> answers;
}