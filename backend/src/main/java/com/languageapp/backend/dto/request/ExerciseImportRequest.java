package com.languageapp.backend.dto.request;

import lombok.Data;
import java.util.Map;

@Data
public class ExerciseImportRequest {
    private String type;
    private Map<String, Object> content;
    private Map<String, Object> correctAnswer;
}