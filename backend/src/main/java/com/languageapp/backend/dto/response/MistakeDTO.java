package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
@lombok.NoArgsConstructor
public class MistakeDTO {
    private UUID exerciseId;
    private String question;
    private String submittedAnswer;
    private String correctAnswer;
}
