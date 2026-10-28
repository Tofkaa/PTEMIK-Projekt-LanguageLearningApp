package com.languageapp.backend.dto.request;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
@Data
public class DynamicPracticeSubmitRequest {
    @NotNull private UUID attemptId;
    @Valid @NotNull @Size(min=1,max=10) private List<@NotNull AnswerResult> results;
    @Min(0) private int timeTakenSeconds; // Informational only, never used for scoring.
    @Data public static class AnswerResult {
        @NotNull private UUID exerciseId;
        @NotNull @Size(max=10000) private String answer;
    }
}
