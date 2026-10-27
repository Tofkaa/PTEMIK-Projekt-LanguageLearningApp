package com.languageapp.backend.repository;

import com.languageapp.backend.entity.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, UUID> {
    List<UserAchievement> findByUserUserId(UUID userId);

    @Query("select ua.achievement.achievementId from UserAchievement ua where ua.user.userId = :userId")
    List<UUID> findOwnedAchievementIds(@Param("userId") UUID userId);
    boolean existsByUserUserIdAndAchievementAchievementId(UUID userId, UUID achievementId);
    @EntityGraph(attributePaths = "achievement")
    List<UserAchievement> findByUserUserIdAndFeedbackEnabledTrueAndPresentedAtIsNullOrderByAchievedAtAsc(UUID userId);
}
