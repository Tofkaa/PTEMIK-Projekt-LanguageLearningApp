package com.languageapp.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VocabularyLookupRequest {
    @jakarta.validation.constraints.NotNull
    private java.util.UUID courseId;
    @NotBlank(message = "A szó nem lehet üres")
    @jakarta.validation.constraints.Size(max = com.languageapp.backend.validation.AnswerLimits.MAX_WORD_CHARACTERS)
    private String word;

    @jakarta.validation.constraints.Size(max = 50)
    private String source = "DICTIONARY_TOOLTIP";
}
