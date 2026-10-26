package com.languageapp.backend.dto.request;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseCheckRequest {
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Size(max = com.languageapp.backend.validation.AnswerLimits.MAX_CHARACTERS,
            message = "A válasz legfeljebb 10 000 karakter lehet.")
    private String answer;
}
