package com.languageapp.backend.dto.response;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseCheckResponse {
    private boolean isCorrect;
    private boolean isAlmostCorrect;
    //private String correctAnswer;
    private String feedbackMessage;
}