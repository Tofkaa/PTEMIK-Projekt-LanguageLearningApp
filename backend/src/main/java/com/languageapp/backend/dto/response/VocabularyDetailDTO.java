package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;

@Data
@AllArgsConstructor
public class VocabularyDetailDTO {
    private String word;
    private String translation;
    private int srsLevel;
    private Instant nextPracticeAt;
    private boolean isMastered;
}