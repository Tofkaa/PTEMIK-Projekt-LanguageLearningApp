package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.AchievementResponse;
import com.languageapp.backend.dto.response.AchievementFeedback;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.languageapp.backend.entity.Achievement;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.entity.UserAchievement;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.enums.ChallengeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.Clock;

/**
 * Core business logic for the Gamification Engine.
 * Evaluates dynamic rules (JSONB criteria) to award trophies to users based on their learning history,
 * social activities, and overall engagement (streaks).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementService {
    private final Clock clock;

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final ProgressRepository progressRepository;
    private final MasteryService masteryService;
    private final UserRepository userRepository;

    // Dependencies for social and engagement achievements
    private final FriendshipRepository friendshipRepository;
    private final SseService sseService;
    private final ChallengeRepository challengeRepository;
    private final StudentVocabularyRepository vocabularyRepository;

    /**
     * Iterates through all available achievements in the system and awards them
     * to the user if they meet the dynamically defined criteria.
     *
     * @param user               The user to be evaluated.
     * @param currentLessonScore The accuracy score (0-100) of the most recently completed lesson.
     */
    @Transactional
    public List<AchievementFeedback> checkAndAwardAchievements(User user, int currentLessonScore) {
        var awarded = new java.util.ArrayList<AchievementFeedback>();
        // Vocabulary practice can trigger achievements too: all award paths share this user lock.
        userRepository.findForLearningUpdate(user.getUserId())
                .orElseThrow(() -> new com.languageapp.backend.exception.ResourceNotFoundException("User not found"));
        List<Achievement> allAchievements = achievementRepository.findAll();

        var owned = new HashSet<>(userAchievementRepository.findOwnedAchievementIds(user.getUserId()));
        // Request-local metrics only: never cache one learner's state across calls or accounts.
        Map<String, Long> metrics = new HashMap<>();
        for (Achievement achievement : allAchievements) {
            if (!achievement.isActive() || owned.contains(achievement.getAchievementId())) continue;
            if (isEligible(user, achievement, currentLessonScore, metrics)) {
                awarded.add(toFeedback(awardAchievement(user, achievement)));
                owned.add(achievement.getAchievementId());
            }
        }
        return awarded;
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
    private boolean isEligible(User user, Achievement achievement, int currentLessonScore, Map<String, Long> metrics) {
        Map<String, Object> criteria = achievement.getCriteria();
        if (criteria == null || !criteria.containsKey("type")) {
            return false;
        }

        String type = String.valueOf(criteria.get("type"));

        try {
            switch (type) {
                case "LESSON_MASTERY":
                    return metrics.computeIfAbsent("LESSON_MASTERY", key -> masteryService.countMastered(user, false)) >= Integer.parseInt(String.valueOf(criteria.get("target")));
                case "TOPIC_MASTERY":
                    return metrics.computeIfAbsent("TOPIC_MASTERY", key -> masteryService.countMastered(user, true)) >= Integer.parseInt(String.valueOf(criteria.get("target")));
                case "FIRST_LESSON":
                    return metrics.computeIfAbsent("COMPLETED_LESSONS", key -> progressRepository.countCompletedLessons(user.getUserId())) >= 1;

                case "LESSON_COUNT":
                    int targetLessons = Integer.parseInt(String.valueOf(criteria.get("target")));
                    return metrics.computeIfAbsent("COMPLETED_LESSONS", key -> progressRepository.countCompletedLessons(user.getUserId())) >= targetLessons;

                case "XP_MILESTONE":
                    int targetXp = Integer.parseInt(String.valueOf(criteria.get("target")));
                    return user.getXp() >= targetXp;

                case "PERFECT_SCORE":
                    return currentLessonScore == 100;

                case "SOCIAL_FRIENDS":
                    int targetFriends = Integer.parseInt(String.valueOf(criteria.get("target")));
                    long currentFriends = metrics.computeIfAbsent("SOCIAL_FRIENDS", key -> friendshipRepository.countFriendships(user.getUserId(), com.languageapp.backend.enums.FriendshipStatus.ACCEPTED));
                    return currentFriends >= targetFriends;

                case "SOCIAL_DUELS":
                    int targetDuels = Integer.parseInt(String.valueOf(criteria.get("target")));
                    long wonDuels = metrics.computeIfAbsent("SOCIAL_DUELS", key -> challengeRepository.countWins(user.getUserId(), ChallengeStatus.COMPLETED));
                    return wonDuels >= targetDuels;

                case "STREAK_DAYS":
                    int targetStreak = Integer.parseInt(String.valueOf(criteria.get("target")));
                    int currentStreak = user.getStreak() != null ? user.getStreak() : 0;
                    return currentStreak >= targetStreak;

                case "VOCAB_COUNT":
                    int targetVocab = Integer.parseInt(String.valueOf(criteria.get("target")));
                    long currentVocabCount = metrics.computeIfAbsent("VOCAB_COUNT", key -> vocabularyRepository.countByUser_UserId(user.getUserId()));
                    return currentVocabCount >= targetVocab;

                case "MASTERED_VOCAB_COUNT":
                    int targetMastered = Integer.parseInt(String.valueOf(criteria.get("target")));
                    long masteredCount = metrics.computeIfAbsent("MASTERED_VOCAB_COUNT", key -> vocabularyRepository.countByUser_UserIdAndSrsLevelGreaterThanEqual(user.getUserId(), 6));
                    return masteredCount >= targetMastered;

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
    @Transactional
    public List<AchievementResponse> getUserAchievements(UUID userId) {
        // Reconcile new mastery trophies with historical passes under the same lock as lesson submissions.
        User user = userRepository.findForLearningUpdate(userId)
                .orElseThrow(() -> new com.languageapp.backend.exception.ResourceNotFoundException("User not found"));
        List<Achievement> allAchievements = achievementRepository.findAll();
        Map<UUID, UserAchievement> owned = new HashMap<>();
        for (UserAchievement award : userAchievementRepository.findByUserUserId(userId)) {
            owned.putIfAbsent(award.getAchievement().getAchievementId(), award);
        }
        Map<String, Long> metrics = new HashMap<>();
        for (Achievement achievement : allAchievements) {
            Object type = achievement.getCriteria() == null ? null : achievement.getCriteria().get("type");
            if (achievement.isActive() && ("LESSON_MASTERY".equals(type) || "TOPIC_MASTERY".equals(type))
                    && !owned.containsKey(achievement.getAchievementId())
                    && isEligible(user, achievement, 0, metrics)) {
                owned.put(achievement.getAchievementId(), awardAchievement(user, achievement));
            }
        }

        return allAchievements.stream().map(ach -> {
            UserAchievement unlockedAch = owned.get(ach.getAchievementId());

            return AchievementResponse.builder()
                    .achievementId(ach.getAchievementId())
                    .name(ach.getName())
                    .description(ach.getDescription())
                    .iconUrl(ach.getIconUrl())
                    .isUnlocked(unlockedAch != null)
                    .achievedAt(unlockedAch != null ? unlockedAch.getAchievedAt() : null)
                    .userAchievementId(unlockedAch != null ? unlockedAch.getUserAchievementId() : null)
                    .unread(unlockedAch != null && unlockedAch.isFeedbackEnabled() && unlockedAch.getReadAt() == null)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Persists a newly earned achievement to the database and logs the event.
     */
    private UserAchievement awardAchievement(User user, Achievement achievement) {
        UserAchievement newAward = new UserAchievement();
        newAward.setUser(user);
        newAward.setAchievement(achievement);
        newAward.setAchievedAt(clock.instant());

        newAward.setFeedbackEnabled(true);
        userAchievementRepository.save(newAward);
        // Never notify clients about an award whose transaction may still roll back.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            String email = user.getEmail();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { sseService.sendPing(email); }
            });
        }
        log.info("🏆 Gamification: New achievement unlocked for user: {}, Achievement: {}", user.getEmail(), achievement.getName());
        return newAward;
    }
    private AchievementFeedback toFeedback(UserAchievement award) {
        Achievement achievement = award.getAchievement();
        return new AchievementFeedback(award.getUserAchievementId(), achievement.getName(),
                achievement.getDescription(), achievement.getIconUrl(), award.getAchievedAt().toString());
    }

    @Transactional(readOnly = true)
    public List<AchievementFeedback> getPendingFeedback(UUID userId) {
        return userAchievementRepository
                .findByUserUserIdAndFeedbackEnabledTrueAndPresentedAtIsNullOrderByAchievedAtAsc(userId)
                .stream().map(this::toFeedback).toList();
    }

    @Transactional
    public void acknowledgeFeedback(UUID userId, List<UUID> awardIds, boolean read) {
        if (awardIds == null || awardIds.isEmpty()) return;
        if (awardIds.size() > 100) throw new com.languageapp.backend.exception.BadRequestException("Túl sok kitüntetés.");
        // Ownership is part of the selection: never acknowledge another user's award.
        userRepository.findForLearningUpdate(userId)
                .orElseThrow(() -> new com.languageapp.backend.exception.ResourceNotFoundException("User not found"));
        for (UserAchievement award : userAchievementRepository.findByUserUserId(userId)) {
            if (!award.isFeedbackEnabled() || !awardIds.contains(award.getUserAchievementId())) continue;
            if (award.getPresentedAt() == null) award.setPresentedAt(clock.instant());
            if (read && award.getReadAt() == null) award.setReadAt(clock.instant());
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void checkSocialAchievements(UUID userId) {
        userRepository.findForLearningUpdate(userId).ifPresent(user -> checkAndAwardAchievements(user, -1));
    }

}
