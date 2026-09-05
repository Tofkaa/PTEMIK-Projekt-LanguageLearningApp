package com.languageapp.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class VocabularyResponse {
    private UUID vocabularyId;
    private String word;
    private String translation;
    private int srsLevel;
    private LocalDateTime nextPracticeAt;
    private boolean isNewAddition;
}