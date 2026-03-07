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
    private String difficulty;
    private String language;
    private String description;
}