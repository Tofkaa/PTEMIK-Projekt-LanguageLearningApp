package com.languageapp.backend.entity;

import com.languageapp.backend.dto.response.DynamicExerciseDTO;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;

/** Server-owned allocation and immutable first answers; no hard practice time limit. */
@Entity @Getter @Setter @NoArgsConstructor
@Table(name="dynamic_practice_attempts",indexes=@Index(name="idx_dynamic_attempt_owner_course",columnList="user_id,course_id,completed_at"))
public class DynamicPracticeAttempt {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID attemptId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="course_id",nullable=false) private Course course;
    @Column(nullable=false) private Instant startedAt;
    private Instant completedAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb",nullable=false) private List<Question> questions;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb",nullable=false) private Map<UUID,String> acceptedAnswers=new LinkedHashMap<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private Map<String,Object> result;
    public record Question(UUID vocabularyId,DynamicExerciseDTO exercise,String expectedAnswer) { }
}
