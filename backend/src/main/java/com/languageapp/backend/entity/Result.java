package com.languageapp.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
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
@Table(name = "results", indexes = {
        @Index(name = "idx_results_user_submitted", columnList = "user_id, submitted_at DESC"),
        @Index(name = "idx_results_user_lesson", columnList = "user_id, lesson_id"),
        @Index(name = "idx_results_challenge_user", columnList = "challenge_id, user_id")
})

public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "result_id", updatable = false, nullable = false)
    private UUID resultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "score")
    private Integer score;

    @Column(name = "time_taken")
    private Integer timeTaken;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "is_challenge_result")
    private Boolean isChallengeResult = false;

    @Column(length = 50)
    private String difficulty;

    @Column(name = "manual_practice", nullable = false, columnDefinition = "boolean default false")
    private boolean manualPractice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id")
    private Challenge challenge;

    @JsonProperty("isTest")
    @Column(name = "is_test_result")
    private Boolean isTestResult = false;

    @Column(name = "correct_answers_count")
    private Integer correctAnswersCount;

    @Column(name = "total_questions_count")
    private Integer totalQuestionsCount;
}
