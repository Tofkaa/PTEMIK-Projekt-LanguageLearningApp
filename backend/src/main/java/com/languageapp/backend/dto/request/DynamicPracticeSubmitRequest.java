package com.languageapp.backend.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class DynamicPracticeSubmitRequest {
    @jakarta.validation.Valid
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Size(max = com.languageapp.backend.validation.AnswerLimits.MAX_WORDS)
    private List<@jakarta.validation.constraints.NotNull AnswerResult> results;
    @jakarta.validation.constraints.Min(0)
    private int timeTakenSeconds;

    @Data
    public static class AnswerResult {
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = com.languageapp.backend.validation.AnswerLimits.MAX_WORD_CHARACTERS)
        private String targetWord;
        private boolean isCorrect;
    }
}
