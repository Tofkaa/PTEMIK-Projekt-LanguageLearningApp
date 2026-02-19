package com.languageapp.backend.repository;

import com.languageapp.backend.entity.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, UUID> {
    List<UserAchievement> findByUserUserId(UUID userId);
    boolean existsByUserUserIdAndAchievementAchievementId(UUID userId, UUID achievementId);
}