package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for sending exercises to the client.
 * <p>
 * SECURITY NOTICE: The 'correctAnswer' field from the Exercise entity
 * is intentionally omitted here to prevent clients from cheating.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExerciseResponse {
    private UUID exerciseId;
    private UUID lessonId;
    private String type;

    private Map<String, Object> content;

    private String audioUrl;
    private String imageUrl;
}