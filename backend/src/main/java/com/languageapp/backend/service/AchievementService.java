package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.AchievementResponse;
import com.languageapp.backend.entity.Achievement;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.entity.UserAchievement;
import com.languageapp.backend.repository.AchievementRepository;
import com.languageapp.backend.repository.ProgressRepository;
import com.languageapp.backend.repository.UserAchievementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core business logic for the Gamification Engine.
 * Evaluates dynamic rules (JSONB criteria) to award trophies to users upon lesson completion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final ProgressRepository progressRepository;

    /**
     * Iterates through all available achievements in the system and awards them
     * to the user if they meet the dynamically defined criteria.
     *
     * @param user               The user who just completed a lesson.
     * @param currentLessonScore The accuracy score (0-100) of the most recently completed lesson.
     */
    @Transactional
    public void checkAndAwardAchievements(User user, int currentLessonScore) {
        List<Achievement> allAchievements = achievementRepository.findAll();

        for (Achievement achievement : allAchievements) {
            // OPTIMIZATION: Check via a highly efficient native query if the user already has this trophy
            if (userAchievementRepository
                    .existsByUserUserIdAndAchievementAchievementId(
                            user.getUserId(),
                            achievement.getAchievementId())) {
                continue; // Skip evaluation if already awarded
            }

            // Evaluate the JSONB conditions
            if (isEligible(user, achievement, currentLessonScore)) {
                awardAchievement(user, achievement);
            }
        }
    }

    /**
     * Parses and evaluates the JSONB 'criteria' column of an Achievement.
     * Functions as a dynamic rule engine.
     */
    private boolean isEligible(User user, Achievement achievement, int currentLessonScore) {
        Map<String, Object> criteria = achievement.getCriteria();
        if (criteria == null || !criteria.containsKey("type")) {
            return false;
        }

        String type = String.valueOf(criteria.get("type"));

        try {
            // Helper variable: Count the total number of successfully completed lessons for this user
            long completedCount = progressRepository.findAll().stream()
                    .filter(p -> p.getUser().getUserId().equals(user.getUserId()) && p.getIsCompleted())
                    .count();

            // Evaluate based on the dynamic "type" defined in the database
            switch (type) {
                case "FIRST_LESSON":
                    return completedCount >= 1;

                case "LESSON_COUNT":
                    int targetLessons = Integer.parseInt(String.valueOf(criteria.get("target")));
                    return completedCount >= targetLessons;

                case "XP_MILESTONE":
                    int targetXp = Integer.parseInt(String.valueOf(criteria.get("target")));
                    return user.getXp() >= targetXp;

                case "PERFECT_SCORE":
                    // Requires the user to achieve a flawless 100% on the current lesson
                    return currentLessonScore == 100;

                default:
                    log.warn("Unknown achievement criteria type: {}", type);
                    return false;
            }
        } catch (Exception e) {
            log.error("Error evaluating achievement criteria (ID: {}): {}", achievement.getAchievementId(), e.getMessage());
            return false;
        }
    }

    /**
     * Aggregates all system achievements and maps them to DTOs, flagging which ones
     * the specific user has already unlocked. Used for the Frontend Profile Screen.
     */
    @Transactional(readOnly = true)
    public List<AchievementResponse> getUserAchievements(UUID userId) {
        List<Achievement> allAchievements = achievementRepository.findAll();
        List<UserAchievement> userAchievements = userAchievementRepository.findByUserUserId(userId);

        return allAchievements.stream().map(ach -> {
            // Check if the current achievement exists in the user's earned list
            UserAchievement unlockedAch = userAchievements.stream()
                    .filter(ua -> ua.getAchievement().getAchievementId().equals(ach.getAchievementId()))
                    .findFirst()
                    .orElse(null);

            return AchievementResponse.builder()
                    .achievementId(ach.getAchievementId())
                    .name(ach.getName())
                    .description(ach.getDescription())
                    .iconUrl(ach.getIconUrl())
                    .isUnlocked(unlockedAch != null) // Handled safely for Jackson via @JsonProperty in the DTO
                    .achievedAt(unlockedAch != null ? unlockedAch.getAchievedAt() : null)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Persists a newly earned achievement to the database and logs the event.
     */
    private void awardAchievement(User user, Achievement achievement) {
        UserAchievement newAward = new UserAchievement();
        newAward.setUser(user);
        newAward.setAchievement(achievement);
        newAward.setAchievedAt(LocalDateTime.now());

        userAchievementRepository.save(newAward);
        log.info("🏆 Gamification: New achievement unlocked for user: {}, Achievement: {}", user.getEmail(), achievement.getName());
    }
}