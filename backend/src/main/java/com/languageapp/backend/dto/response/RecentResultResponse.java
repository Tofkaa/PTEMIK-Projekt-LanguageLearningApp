package com.languageapp.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class RecentResultResponse {
    private String lessonTitle;
    private String difficulty;
    private int score;
    private int correctAnswers;
    private int totalQuestions;
    private Instant submittedAt;
}