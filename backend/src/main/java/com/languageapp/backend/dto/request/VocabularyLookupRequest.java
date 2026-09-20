package com.languageapp.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VocabularyLookupRequest {
    @NotBlank(message = "A szó nem lehet üres")
    private String word;

    private String source = "DICTIONARY_TOOLTIP";
}