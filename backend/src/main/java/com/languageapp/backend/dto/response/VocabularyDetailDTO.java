package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class VocabularyDetailDTO {
    private String word;
    private String translation;
    private int srsLevel;
    private LocalDateTime nextPracticeAt;
    private boolean isMastered;
}