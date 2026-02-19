package com.languageapp.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_achievements")
@Data
public class UserAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_achievement_id", updatable = false, nullable = false)
    private UUID userAchievementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    @CreationTimestamp
    @Column(name = "achieved_at", nullable = false, updatable = false)
    private LocalDateTime achievedAt;
}