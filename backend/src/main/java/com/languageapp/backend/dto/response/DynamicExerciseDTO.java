package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class DynamicExerciseDTO {
    private UUID exerciseId;
    private String type; // MULTIPLE_CHOICE, WORD_BANK, TRANSLATION
    private String question;
    private String targetWord;
    private String correctAnswer;
    private List<String> options;
    private String hint;
}