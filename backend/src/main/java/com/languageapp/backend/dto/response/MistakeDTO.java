package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MistakeDTO {
    private String question;
    private String submittedAnswer;
    private String correctAnswer;
}