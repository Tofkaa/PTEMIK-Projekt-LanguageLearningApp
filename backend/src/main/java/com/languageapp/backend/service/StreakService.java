package com.languageapp.backend.service;

import com.languageapp.backend.entity.User;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Service responsible for managing user streaks and daily activity tracking.
 * Utilizes automated scheduled tasks to enforce streak expiration rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreakService {

    private final UserRepository userRepository;

    /**
     * Updates the user's streak based on their last recorded activity date.
     * Called whenever a user successfully completes a lesson, challenge, or assignment.
     *
     * @param user The user whose activity is being recorded.
     */
    @Transactional
    public void updateActivity(User user) {
        // Enforce a strict timezone to prevent exploits where users change their phone clocks
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Budapest"));
        LocalDate lastActivity = user.getLastActivityDate();

        if (lastActivity == null) {
            // First ever activity
            user.setStreak(1);
        } else if (lastActivity.equals(today)) {
            // Already studied today, do nothing to the streak
            log.debug("User {} already completed an activity today. Streak remains {}.", user.getEmail(), user.getStreak());
            return;
        } else if (lastActivity.equals(today.minusDays(1))) {
            // Studied yesterday, increment the streak safely
            user.setStreak(user.getStreak() + 1);
        } else {
            // Skipped at least one day, reset the chain back to 1
            user.setStreak(1);
        }

        user.setLastActivityDate(today);
        userRepository.save(user);
        log.info("Streak activity updated for user {}. New streak: {}", user.getEmail(), user.getStreak());
    }

    /**
     * Scheduled background job that executes every day at 00:05 AM (Europe/Budapest time).
     * Identifies all users who failed to complete an activity yesterday and resets their streaks to 0.
     * Uses a bulk JPA query for optimal database performance.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Europe/Budapest")
    @Transactional
    public void resetExpiredStreaks() {
        log.info("Starting scheduled night-job: Streak reset...");
        LocalDate yesterday = LocalDate.now(ZoneId.of("Europe/Budapest")).minusDays(1);

        int resetCount = userRepository.resetExpiredStreaks(yesterday);

        log.info("Scheduled streak reset completed successfully. Reset {} user(s) to 0 streak.", resetCount);
    }
}