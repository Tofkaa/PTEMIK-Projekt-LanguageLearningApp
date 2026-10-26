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
@Table(name = "assignment_sessions", indexes = {
        @Index(name = "idx_sessions_assignment_user", columnList = "assignment_id, user_id"),
        @Index(name = "idx_sessions_assignment_started", columnList = "assignment_id, started_at DESC")
})
public class AssignmentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private ClassroomAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "final_score")
    private Integer finalScore;

    @Column(name = "teacher_score")
    private Integer teacherScore;

    @Column(name = "teacher_comment", columnDefinition = "TEXT")
    private String teacherComment;

    @Column(name = "is_graded", columnDefinition = "boolean default false")
    private boolean isGraded = false;

    @Column(name = "raw_answers", columnDefinition = "TEXT")
    private String rawAnswers;

    @Column(columnDefinition = "TEXT")
    private String allocatedExerciseIds;
    @Column(columnDefinition = "TEXT")
    private String draftState;
    /** Input revision while open; publication revision after the attempt is finished. */
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long draftRevision;
    private Instant draftSavedAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean timingInitialized;
    private Integer effectiveTimeLimitMinutes;
    private Instant effectiveAvailableUntil;

}
