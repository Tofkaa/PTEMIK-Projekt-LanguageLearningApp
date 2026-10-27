package com.languageapp.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * DTO for evaluating the whole lesson (request from frontend)
 * Contains all answers by the student, and the time spent as well.
 */
@Data
public class LessonSubmitRequest {
    private java.util.UUID attemptId;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Min(0)
    private Integer timeTakenSeconds = 0;

    @NotEmpty(message = "The List of answers can not be empty")
    @jakarta.validation.constraints.Size(max = com.languageapp.backend.validation.AnswerLimits.MAX_LESSON_SUBMISSIONS)
    @Valid // Tells Spring to validate items in the List as well.
    private List<@jakarta.validation.constraints.NotNull ExerciseSubmission> answers;
}
