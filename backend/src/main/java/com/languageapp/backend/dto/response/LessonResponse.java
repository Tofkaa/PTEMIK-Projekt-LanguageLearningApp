package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Data Transfer Object for transferring Lesson information to the client.
 * Provides a clean summary of the lesson without exposing internal database structures.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LessonResponse {
    private UUID lessonId;
    private String topicName;
    private String title;
    private Integer orderIndex;
    private String language;
    private String description;
    private boolean isCompleted;
    private String completedDifficulty;
    private String difficulty;
    private java.util.List<String> availableDifficulties;
    private java.util.List<String> completedDifficulties;
    private boolean mastered;
    private boolean topicMastered;

    public LessonResponse(UUID lessonId, String topicName, String title, Integer orderIndex, String language,
                          String description, boolean isCompleted, String completedDifficulty, String difficulty) {
        this.lessonId = lessonId;
        this.topicName = topicName;
        this.title = title;
        this.orderIndex = orderIndex;
        this.language = language;
        this.description = description;
        this.isCompleted = isCompleted;
        this.completedDifficulty = completedDifficulty;
        this.difficulty = difficulty;
    }
}
