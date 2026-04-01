package com.languageapp.backend.service;

import com.languageapp.backend.entity.Result;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.repository.ResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

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
        // 1. Check for manual difficulty override
        if (!"DYNAMIC".equals(user.getPreferredDifficulty().name())) return user
                .getPreferredDifficulty()
                .name();

        // Fetch the 5 most recent results to analyze the learning trend
        List<Result> recentResults = resultRepository
                .findTop5ByUserUserIdOrderBySubmittedAtDesc(user.getUserId());

        // Default to MEDIUM if the user has no prior history
        if (recentResults.isEmpty()) {
            return "MEDIUM";
        }

        // 2. Calculate the weighted average of recent scores
        double weightedScore = calculateWeightedAverage(recentResults);

        // 3. Determine the difficulty of the most recently completed lesson
        String lastDifficulty = recentResults.getFirst().getLesson() != null
                ? recentResults.getFirst().getLesson().getDifficulty()
                : "MEDIUM";

        log.debug("Adaptive Eval -> User: {}, Last Difficulty: {}, Weighted Score: {}",
                user.getEmail(), lastDifficulty, String.format("%.2f", weightedScore));

        // 4. Apply hysteresis to determine the next difficulty level
        return calculateNextDifficulty(lastDifficulty, weightedScore, recentResults.size());
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
     * Determines the next difficulty using hysteresis to prevent erratic level changes.
     *
     * @param currentDifficulty the difficulty of the most recent lesson
     * @param score the calculated weighted average score
     * @param totalAttempts the total number of recorded attempts
     * @return the assigned difficulty level for the next lesson
     */
    private String calculateNextDifficulty(String currentDifficulty, double score, int totalAttempts) {
        // Protection: Require at least 3 attempts before promoting to HARD
        if (totalAttempts < 3 && score > 85.0) {
            return "MEDIUM";
        }

        switch (currentDifficulty) {
            case "HARD":
                // Forgiving threshold: Since HARD is inherently difficult,
                // the user only drops to MEDIUM if performance consistently falls below 60%
                if (score < 60.0) return "MEDIUM";
                return "HARD";

            case "EASY":
                // Fast promotion: Quickly graduate from EASY to prevent boredom
                if (score >= 75.0) return "MEDIUM";
                return "EASY";

            case "MEDIUM":
            default:
                // Standard thresholds for the base difficulty
                if (score >= 85.0) return "HARD";
                if (score < 50.0) return "EASY";
                return "MEDIUM";
        }
    }
}