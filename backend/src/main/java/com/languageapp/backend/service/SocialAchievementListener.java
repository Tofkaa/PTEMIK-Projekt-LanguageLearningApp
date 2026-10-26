package com.languageapp.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class SocialAchievementListener {
    private final AchievementService achievementService;

    @TransactionalEventListener
    public void onSocialActivity(SocialAchievementEvent event) {
        for (var userId : event.userIds().stream().distinct().sorted().toList()) {
            try { achievementService.checkSocialAchievements(userId); }
            catch (RuntimeException error) {
                log.error("Social achievement evaluation failed for {}", userId, error);
            }
        }
    }
}
