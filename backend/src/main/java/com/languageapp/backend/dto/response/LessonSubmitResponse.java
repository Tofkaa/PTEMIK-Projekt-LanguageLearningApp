package com.languageapp.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object (DTO) containing the comprehensive result of a lesson evaluation.
 * * Transmits the score, earned XP, specific mistakes, and the updated gamification state (Streak)
 * back to the client for immediate UI rendering.
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
    private List<MistakeDTO> mistakes;
    private int newStreak;
}