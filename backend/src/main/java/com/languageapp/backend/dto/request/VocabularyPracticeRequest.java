package com.languageapp.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VocabularyPracticeRequest {
    @NotNull(message = "A gyakorlás eredménye kötelező")
    private Boolean isCorrect;
}