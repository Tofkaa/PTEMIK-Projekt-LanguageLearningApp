package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.DynamicPracticeSubmitRequest;
import com.languageapp.backend.dto.request.VocabularyLookupRequest;
import com.languageapp.backend.dto.request.VocabularyPracticeRequest;
import com.languageapp.backend.dto.response.DynamicExerciseDTO;
import com.languageapp.backend.dto.response.VocabularyDetailDTO;
import com.languageapp.backend.dto.response.VocabularyResponse;
import com.languageapp.backend.service.VocabularyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
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

    @GetMapping("/due")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<VocabularyResponse>> getDueVocabulary(Authentication authentication) {
        List<VocabularyResponse> dueWords = vocabularyService.getDueVocabularyForToday(authentication.getName());
        return ResponseEntity.ok(dueWords);
    }

    @GetMapping("/learned")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Integer>> getVocabularyMap(Authentication authentication) {
        Map<String, Integer> vocabMap = vocabularyService.getVocabularyMap(authentication.getName());
        return ResponseEntity.ok(vocabMap);
    }

    @PostMapping("/known-batch")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> addKnownWordsBatch(
            Authentication authentication,
            @RequestBody Map<String, List<String>> request) {

        vocabularyService.addKnownWordsBatch(authentication.getName(), request.get("words"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/hub")
    public ResponseEntity<List<VocabularyDetailDTO>> getVocabularyHub(Principal principal) {
        return ResponseEntity.ok(vocabularyService.getDetailedVocabularyForUser(principal.getName()));
    }

    @GetMapping("/practice/generate")
    public ResponseEntity<List<DynamicExerciseDTO>> generatePracticeSession(Principal principal) {
        return ResponseEntity.ok(vocabularyService.generateDynamicPracticeSession(principal.getName(), 10));
    }

    @PostMapping("/practice/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> submitDynamicPractice(
            Principal principal,
            @RequestBody DynamicPracticeSubmitRequest request) {

        Map<String, Object> result = vocabularyService.processDynamicPracticeSession(principal.getName(), request);
        return ResponseEntity.ok(result);
    }
}