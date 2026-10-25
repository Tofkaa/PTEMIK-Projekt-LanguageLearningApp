package com.languageapp.backend.entity;

import com.languageapp.backend.dto.response.LessonSubmitResponse;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;

/** Server-owned allocation: profile changes cannot change an already started lesson. */
@Entity
@Table(name = "lesson_attempts")
@Getter @Setter @NoArgsConstructor
public class LessonAttempt {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID attemptId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private Lesson lesson;
    private UUID challengeId;
    @Column(nullable = false)
    private String difficulty;
    private boolean manualPractice;
    private Instant startedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false)
    private List<UUID> exerciseIds;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", nullable = false)
    private List<AllocatedExercise> exerciseSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    private LessonSubmitResponse submissionResult;

    public record AllocatedExercise(UUID exerciseId, String type, String difficulty,
                                    Map<String, Object> content, Map<String, Object> correctAnswer) {
        public static AllocatedExercise capture(Exercise exercise) {
            return new AllocatedExercise(exercise.getExerciseId(), exercise.getType(), exercise.getDifficulty(),
                    exercise.getContent(), exercise.getCorrectAnswer());
        }
        public Exercise asExercise() {
            Exercise exercise = new Exercise(); exercise.setExerciseId(exerciseId);
            exercise.setType(type); exercise.setDifficulty(difficulty);
            exercise.setContent(content); exercise.setCorrectAnswer(correctAnswer);
            return exercise;
        }
    }
}
