package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.VocabularyLookupRequest;
import com.languageapp.backend.dto.request.VocabularyPracticeRequest;
import com.languageapp.backend.dto.response.VocabularyResponse;
import com.languageapp.backend.service.VocabularyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/vocabulary")
@RequiredArgsConstructor
public class VocabularyController {

    private final VocabularyService vocabularyService;

    @PostMapping("/lookup")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<VocabularyResponse> lookupWord(
            Authentication authentication,
            @Valid @RequestBody VocabularyLookupRequest request) {

        VocabularyResponse response = vocabularyService.lookupAndSaveWord(
                authentication.getName(),
                request.getWord(),
                request.getSource()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/practice")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<VocabularyResponse> recordPractice(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody VocabularyPracticeRequest request) {

        VocabularyResponse response = vocabularyService.recordPracticeResult(
                id,
                authentication.getName(),
                request.getIsCorrect()
        );
        return ResponseEntity.ok(response);
    }
}