package com.languageapp.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "results")
@Data
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
    private LocalDateTime submittedAt;

    @Column(name = "is_challenge_result")
    private Boolean isChallengeResult = false;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id")
    private Challenge challenge;

    @Column(name = "is_test_result")
    private Boolean isTestResult = false;

    @Column(name = "correct_answers_count")
    private Integer correctAnswersCount;

    @Column(name = "total_questions_count")
    private Integer totalQuestionsCount;
}