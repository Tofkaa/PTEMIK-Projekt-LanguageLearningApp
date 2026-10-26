package com.languageapp.backend.entity;

import com.languageapp.backend.enums.ChallengeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "challenges", indexes = {
        @Index(name = "idx_challenges_challenger_status", columnList = "challenger_id, status"),
        @Index(name = "idx_challenges_opponent_status", columnList = "opponent_id, status"),
        @Index(name = "idx_challenges_status_end", columnList = "status, end_time")
})

public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "challenge_id", updatable = false, nullable = false)
    private UUID challengeId;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenger_id", nullable = false)
    private User challenger;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opponent_id", nullable = false)
    private User opponent;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @Column(name = "target_difficulty", length = 50)
    private String targetDifficulty;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ChallengeStatus status;
}
