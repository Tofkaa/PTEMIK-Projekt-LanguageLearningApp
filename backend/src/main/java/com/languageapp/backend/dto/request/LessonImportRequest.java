package com.languageapp.backend.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class LessonImportRequest {
    private String title;
    private Integer orderIndex;
    private String difficulty;
    private String description;
    private String language;
    private List<ExerciseImportRequest> exercises;
}