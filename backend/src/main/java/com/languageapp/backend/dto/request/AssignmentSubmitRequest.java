package com.languageapp.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * Request DTO for submitting completed assignment answers.
 * Instead of a score, it carries the raw exercise submissions to be graded server-side.
 */
@Data
public class AssignmentSubmitRequest {

    @NotNull(message = "The list of answers cannot be null")
    @jakarta.validation.constraints.Size(max = com.languageapp.backend.validation.AnswerLimits.MAX_ASSIGNMENT_SUBMISSIONS)
    @Valid
    private List<@NotNull ExerciseSubmission> answers;

    @NotNull(message = "A mentési verzió kötelező")
    private Long revision;
}
