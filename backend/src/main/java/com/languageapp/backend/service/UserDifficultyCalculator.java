package com.languageapp.backend.service;

import com.languageapp.backend.entity.Result;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.repository.ResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Component responsible for calculating the dynamic difficulty level for a user.
 * Extracted to ensure DRY and Single Responsibility principles.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDifficultyCalculator {

    private final ResultRepository resultRepository;

    /**
     * Core Business Logic for Adaptive Learning.
     * Determines the exact difficulty level based on user preference and past performance.
     */
    public String determineTargetDifficulty(User user) {
        if (!"DYNAMIC".equals(user.getPreferredDifficulty().name())) {
            return user.getPreferredDifficulty().name();
        }

        List<Result> recentResults = resultRepository.findTop3ByUserUserIdOrderBySubmittedAtDesc(user.getUserId());

        if (recentResults.isEmpty()) {
            return "MEDIUM";
        }

        double averageScore = recentResults.stream()
                .mapToInt(Result::getScore)
                .average()
                .orElse(0.0);

        log.debug("User's average score over last 3 lessons: {}", averageScore);

        if (averageScore >= 85.0) {
            return "HARD";
        } else if (averageScore < 50.0) {
            return "EASY";
        } else {
            return "MEDIUM";
        }
    }
}