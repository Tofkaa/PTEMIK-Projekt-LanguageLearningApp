package com.languageapp.backend.dto.request;

import com.languageapp.backend.validation.AnswerLimits;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/** Keeps the existing {words:[...]} JSON contract with explicit item and collection bounds. */
@Data
public class KnownWordsRequest {
    @jakarta.validation.constraints.NotNull
    private java.util.UUID courseId;
    @NotNull @Size(max = AnswerLimits.MAX_WORDS)
    private List<@NotBlank @Size(max = AnswerLimits.MAX_WORD_CHARACTERS) String> words;
}
