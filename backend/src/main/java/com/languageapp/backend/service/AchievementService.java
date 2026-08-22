package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.AchievementResponse;
import com.languageapp.backend.entity.Achievement;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.entity.UserAchievement;
import com.languageapp.backend.repository.AchievementRepository;
import com.languageapp.backend.repository.ProgressRepository;
import com.languageapp.backend.repository.UserAchievementRepository;
import com.languageapp.backend.repository.ChallengeRepository;
import com.languageapp.backend.enums.ChallengeStatus;
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
 * Evaluates dynamic rules (JSONB criteria) to award trophies to users based on their learning history,
 * social activities, and overall engagement (streaks).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final ProgressRepository progressRepository;

    // Dependencies for social and engagement achievements
    private final FriendshipService friendshipService;
    private final ChallengeRepository challengeRepository;

    /**
     * Iterates through all available achievements in the system and awards them
     * to the user if they meet the dynamically defined criteria.
     *
     * @param user               The user to be evaluated.
     * @param currentLessonScore The accuracy score (0-100) of the most recently completed lesson.
     */
    @Transactional
    public void checkAndAwardAchievements(User user, int currentLessonScore) {
        List<Achievement> allAchievements = achievementRepository.findAll();

        for (Achievement achievement : allAchievements) {
            // OPTIMIZATION: Check if the user already has this trophy to prevent redundant processing
            if (userAchievementRepository
                    .existsByUserUserIdAndAchievementAchievementId(
                            user.getUserId(),
                            achievement.getAchievementId())) {
                continue;
            }

            // Evaluate the JSONB conditions dynamically
            if (isEligible(user, achievement, currentLessonScore)) {
                awardAchievement(user, achievement);
            }
        }
    }

    /**
     * Parses and evaluates the JSONB 'criteria' column of an Achievement.
     * Functions as a dynamic rule engine mapping to specific backend metrics.
     *
     * @param user The authenticated user.
     * @param achievement The specific achievement being evaluated.
     * @param currentLessonScore The score achieved in the triggering lesson.
     * @return boolean True if the criteria are fully met.
     */
    private boolean isEligible(User user, Achievement achievement, int currentLessonScore) {
        Map<String, Object> criteria = achievement.getCriteria();
        if (criteria == null || !criteria.containsKey("type")) {
            return false;
        }

        String type = String.valueOf(criteria.get("type"));

        try {
            // Helper metric: Total successfully completed lessons
            long completedCount = progressRepository.findAll().stream()
                    .filter(p -> p.getUser().getUserId().equals(user.getUserId()) && p.getIsCompleted())
                    .count();

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
                    return currentLessonScore == 100;

                case "SOCIAL_FRIENDS":
                    int targetFriends = Integer.parseInt(String.valueOf(criteria.get("target")));
                    int currentFriends = friendshipService.getMyFriends(user.getUserId()).size();
                    return currentFriends >= targetFriends;

                case "SOCIAL_DUELS":
                    int targetDuels = Integer.parseInt(String.valueOf(criteria.get("target")));
                    long wonDuels = challengeRepository.findByChallengerUserIdOrOpponentUserId(user.getUserId(), user.getUserId())
                            .stream()
                            .filter(c -> c.getStatus() == ChallengeStatus.COMPLETED
                                    && c.getWinner() != null
                                    && c.getWinner().getUserId().equals(user.getUserId()))
                            .count();
                    return wonDuels >= targetDuels;

                case "STREAK_DAYS":
                    int targetStreak = Integer.parseInt(String.valueOf(criteria.get("target")));
                    int currentStreak = user.getStreak() != null ? user.getStreak() : 0;
                    return currentStreak >= targetStreak;

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
     *
     * @param userId The UUID of the authenticated user.
     * @return List of {@link AchievementResponse} DTOs.
     */
    @Transactional(readOnly = true)
    public List<AchievementResponse> getUserAchievements(UUID userId) {
        List<Achievement> allAchievements = achievementRepository.findAll();
        List<UserAchievement> userAchievements = userAchievementRepository.findByUserUserId(userId);

        return allAchievements.stream().map(ach -> {
            UserAchievement unlockedAch = userAchievements.stream()
                    .filter(ua -> ua.getAchievement().getAchievementId().equals(ach.getAchievementId()))
                    .findFirst()
                    .orElse(null);

            return AchievementResponse.builder()
                    .achievementId(ach.getAchievementId())
                    .name(ach.getName())
                    .description(ach.getDescription())
                    .iconUrl(ach.getIconUrl())
                    .isUnlocked(unlockedAch != null)
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