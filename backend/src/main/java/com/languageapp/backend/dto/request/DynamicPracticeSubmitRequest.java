package com.languageapp.backend.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class DynamicPracticeSubmitRequest {
    private List<AnswerResult> results;
    private int timeTakenSeconds;

    @Data
    public static class AnswerResult {
        private String targetWord;
        private boolean isCorrect;
    }
}