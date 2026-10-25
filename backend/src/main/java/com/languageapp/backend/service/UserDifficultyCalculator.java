package com.languageapp.backend.service;

import com.languageapp.backend.entity.Result;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.DifficultyLevel;
import com.languageapp.backend.repository.ResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Advanced Dynamic Difficulty Calculator.
 * <p>
 * Utilizes a Weighted Moving Average (WMA) and Hysteresis logic to prevent
 * difficulty "ping-ponging" and accurately track the user's learning curve.
 * It also respects manual difficulty overrides set in the user's profile.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDifficultyCalculator {

    private final ResultRepository resultRepository;

    /**
     * Determines the optimal target difficulty for the specified user.
     *
     * @param user the authenticated user entity
     * @return the calculated or preferred difficulty level ("EASY", "MEDIUM", or "HARD")
     */
    public String determineTargetDifficulty(User user) {
        return determineTargetDifficulty(user, user.getActiveCourse().getCourseId());
    }

    public String determineTargetDifficulty(User user, UUID activeCourseId) {
        // 1. Check for manual difficulty override
        if (!"DYNAMIC".equals(user.getPreferredDifficulty().name())) {
            return user.getPreferredDifficulty().name();
        }

        List<Result> recentResults = resultRepository
                .findTop5ByUserUserIdAndLesson_Topic_Course_CourseIdAndIsChallengeResultFalseAndManualPracticeFalseAndIsTestResultFalseOrderBySubmittedAtDesc(user.getUserId(), activeCourseId);
        // Default to EASY if the user has no prior history IN THIS SPECIFIC COURSE
        if (recentResults.isEmpty()) {
            return DifficultyLevel.EASY.name();
        }

        // 2. Calculate the weighted average of recent scores
        double weightedScore = calculateWeightedAverage(recentResults);

        // 3. Determine the difficulty directly from the most recent Result entity
        String lastDifficulty = recentResults.getFirst().getDifficulty() != null
                ? recentResults.getFirst().getDifficulty()
                : "MEDIUM";

        log.debug("Adaptive Eval -> User: {}, Course: {}, Last Difficulty: {}, Weighted Score: {}",
                user.getEmail(), user.getActiveCourse().getLanguageCode(), lastDifficulty, String.format("%.2f", weightedScore));

        long totalLifetimeAttempts = resultRepository
                .countByUserUserIdAndLesson_Topic_Course_CourseIdAndIsChallengeResultFalseAndManualPracticeFalseAndIsTestResultFalse(user.getUserId(), activeCourseId);

        return calculateNextDifficulty(lastDifficulty, weightedScore, (int) totalLifetimeAttempts);
    }

    /**
     * Calculates the Weighted Moving Average (WMA) of recent results.
     * Recent attempts carry significantly more weight to reward fresh progress.
     *
     * @param results the list of recent lesson results
     * @return the weighted average score
     */
    private double calculateWeightedAverage(List<Result> results) {
        // Weights: Most recent (40%), 2nd (25%), 3rd (15%), 4th (10%), 5th (10%)
        double[] weights = {0.40, 0.25, 0.15, 0.10, 0.10};
        double totalWeight = 0.0;
        double weightedSum = 0.0;

        for (int i = 0; i < results.size(); i++) {
            // Safely assign weight, falling back to the last defined weight if array bounds are exceeded
            double weight = weights[Math.min(i, weights.length - 1)];
            weightedSum += results.get(i).getScore() * weight;
            totalWeight += weight;
        }

        return totalWeight == 0 ? 0 : (weightedSum / totalWeight);
    }

    /**
     * Determines the next difficulty using hysteresis and pacing to prevent erratic level changes.
     *
     * @param currentDifficulty the difficulty of the most recent lesson
     * @param score the calculated weighted average score (WMA)
     * @param totalAttempts the total lifetime number of recorded attempts for the user
     * @return the assigned difficulty level for the next lesson
     */
    private String calculateNextDifficulty(String currentDifficulty, double score, int totalAttempts) {
        switch (currentDifficulty) {
            case "HARD":
                // If WMA drops below 60% demote the user back to MEDIUM for a better experience
                if (score < 60.0) return "MEDIUM";
                return "HARD";

            case "EASY":
                // Pacing buffer: Require at least 3 completed lessons (approx. one topic)
                // AND a 75%+ WMA score to graduate to the MEDIUM level.
                if (score >= 75.0 && totalAttempts >= 3) return "MEDIUM";
                return "EASY";

            case "MEDIUM":
            default:
                // Gradual promotion: Require at least 6 total lifetime lessons.
                // Since 3 were spent on EASY, this guarantees at least 3 lessons on MEDIUM before reaching HARD.
                if (score >= 85.0 && totalAttempts >= 6) return "HARD";

                // Immediate safety net: If performance drops below 50%,
                // instantly demote to EASY for practice (no delay needed here).
                if (score < 50.0) return "EASY";

                return "MEDIUM";
        }
    }
}
