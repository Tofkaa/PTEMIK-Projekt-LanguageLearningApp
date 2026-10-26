package com.languageapp.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "progress", indexes = {
        @Index(name = "idx_progress_user_lesson_level", columnList = "user_id, lesson_id, completed_difficulty")
})

public class Progress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "progress_id", updatable = false, nullable = false)
    private UUID progressId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "completed_difficulty", length = 50)
    private String completedDifficulty;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "highest_score")
    private Integer highestScore;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "is_completed")
    private Boolean isCompleted = false;
}
