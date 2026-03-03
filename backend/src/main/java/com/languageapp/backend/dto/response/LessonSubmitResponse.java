package com.languageapp.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * DTO containing the result of the evaluation.
 * Returns points, XP and statistics.
 */
@Data
@Builder
public class LessonSubmitResponse {
    private UUID resultId;
    private int score; // Percentage (0-100)
    private int correctAnswersCount;
    private int totalQuestionsCount;
    private int xpEarned;
    private boolean passed;
    private String feedback;
}