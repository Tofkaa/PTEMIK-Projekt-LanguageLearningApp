package com.languageapp.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a vocabulary word saved by a student.
 * Tracks the Spaced Repetition System (SRS) level and scheduling metrics.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_vocabulary")
public class StudentVocabulary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "vocabulary_id", updatable = false, nullable = false)
    private UUID vocabularyId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The target language word or phrase.
     */
    @Column(nullable = false, length = 100)
    private String word;

    /**
     * The native language translation.
     */
    @Column(nullable = false, length = 200)
    private String translation;

    /**
     * The origin of the saved word (e.g., "DICTIONARY_LOOKUP", "FAILED_TEST").
     */
    @Column(length = 50)
    private String source;

    /**
     * Current SRS mastery level (e.g., 0 = New/Failed, 5 = Mastered).
     */
    @Column(name = "srs_level", nullable = false)
    private int srsLevel = 0;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_practiced_at")
    private LocalDateTime lastPracticedAt;

    /**
     * The scheduled date and time for the next review session.
     */
    @Column(name = "next_practice_at", nullable = false)
    private LocalDateTime nextPracticeAt;

    /**
     * Initializes default timestamps before persisting a new entity.
     */
    @PrePersist
    protected void onCreate() {
        this.firstSeenAt = LocalDateTime.now();
        if (this.nextPracticeAt == null) {
            this.nextPracticeAt = LocalDateTime.now();
        }
    }
}