package com.languageapp.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TopicAdminResponse {
    private UUID topicId;
    private String topicName;
    private List<LessonDto> lessons;

    @Data
    @Builder
    public static class LessonDto {
        private UUID lessonId;
        private String title;
        private String difficulty;
        private List<ExerciseDto> exercises;
    }

    @Data
    @Builder
    public static class ExerciseDto {
        private UUID exerciseId;
        private String type;
        private Object content;
        private Object correctAnswer;
    }
}