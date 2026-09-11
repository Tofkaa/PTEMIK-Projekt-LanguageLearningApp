package com.languageapp.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.languageapp.backend.dto.response.VocabularyResponse;
import com.languageapp.backend.entity.StudentVocabulary;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.ForbiddenException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.StudentVocabularyRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyService {

    private final StudentVocabularyRepository vocabularyRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Transactional
    public VocabularyResponse lookupAndSaveWord(String email, String word, String source) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String cleanWord = word.trim().toLowerCase();


        Optional<StudentVocabulary> existing = vocabularyRepository.findByUser_UserIdAndWordIgnoreCase(user.getUserId(), cleanWord);

        if (existing.isPresent()) {
            StudentVocabulary voc = existing.get();
            return VocabularyResponse.builder()
                    .vocabularyId(voc.getVocabularyId())
                    .word(voc.getWord())
                    .translation(voc.getTranslation())
                    .srsLevel(voc.getSrsLevel())
                    .nextPracticeAt(voc.getNextPracticeAt())
                    .isNewAddition(false)
                    .build();
        }

        String translation = fetchTranslationFromExternalApi(cleanWord);

        StudentVocabulary newVoc = new StudentVocabulary();
        newVoc.setUser(user);
        newVoc.setWord(cleanWord);
        newVoc.setTranslation(translation);
        newVoc.setSource(source != null ? source : "DICTIONARY");

        newVoc = vocabularyRepository.save(newVoc);

        log.info("New word '{}' added to user {}'s vocabulary", cleanWord, email);

        return VocabularyResponse.builder()
                .vocabularyId(newVoc.getVocabularyId())
                .word(newVoc.getWord())
                .translation(newVoc.getTranslation())
                .srsLevel(newVoc.getSrsLevel())
                .nextPracticeAt(newVoc.getNextPracticeAt())
                .isNewAddition(true)
                .build();
    }

    /**
     * External API Call for fetching translation from Lingva
     */
    private String fetchTranslationFromExternalApi(String word) {
        try {
            // Lingva API végpont: /api/v1/{forrás_nyelv}/{cél_nyelv}/{szó}
            String url = "https://lingva.ml/api/v1/en/hu/" + word;
            String response = restTemplate.getForObject(url, String.class);

            // JSON feldolgozás: A Lingva a "translation" mezőben adja vissza az eredményt
            JsonNode root = objectMapper.readTree(response);
            String translatedText = root.path("translation").asText();

            return translatedText != null && !translatedText.isEmpty() ? translatedText : "Ismeretlen jelentés";

        } catch (Exception e) {
            log.error("Hiba a külső szótár API hívásakor a '{}' szóra: {}", word, e.getMessage());
            return "Fordítás nem érhető el"; // Fallback hálózati hiba esetén
        }
    }

    /**
     * Processes the training results and updates the SRS levels.
     */
    @Transactional
    public VocabularyResponse recordPracticeResult(UUID vocabularyId, String email, boolean isCorrect) {
        StudentVocabulary voc = vocabularyRepository.findById(vocabularyId)
                .orElseThrow(() -> new ResourceNotFoundException("Szó nem található"));

        if (!voc.getUser().getEmail().equals(email)) {
            throw new ForbiddenException("Nincs jogosultságod módosítani ezt a szót");
        }

        voc.setLastPracticedAt(LocalDateTime.now());

        if (isCorrect) {
            voc.setSrsLevel(Math.min(6, voc.getSrsLevel() + 1));
        } else {
            voc.setSrsLevel(0);
        }

        int daysToAdd = switch (voc.getSrsLevel()) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 7;
            case 4 -> 14;
            case 5 -> 30;
            default -> 90;
        };

        voc.setNextPracticeAt(LocalDateTime.now().plusDays(daysToAdd));
        StudentVocabulary updatedVoc = vocabularyRepository.save(voc);

        return VocabularyResponse.builder()
                .vocabularyId(updatedVoc.getVocabularyId())
                .word(updatedVoc.getWord())
                .translation(updatedVoc.getTranslation())
                .srsLevel(updatedVoc.getSrsLevel())
                .nextPracticeAt(updatedVoc.getNextPracticeAt())
                .isNewAddition(false)
                .build();
    }

    /**
     * Retrieves the words that the student needs to repeat up to the current time.
     * Results are in ascending order of next practice time (oldest due first).
     */
    @Transactional(readOnly = true)
    public List<VocabularyResponse> getDueVocabularyForToday(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));


        LocalDateTime now = LocalDateTime.now();

        List<StudentVocabulary> dueVocabs = vocabularyRepository
                .findAllByUser_UserIdAndNextPracticeAtBeforeOrderByNextPracticeAtAsc(user.getUserId(), now);

        return dueVocabs.stream()
                .map(voc -> VocabularyResponse.builder()
                        .vocabularyId(voc.getVocabularyId())
                        .word(voc.getWord())
                        .translation(voc.getTranslation())
                        .srsLevel(voc.getSrsLevel())
                        .nextPracticeAt(voc.getNextPracticeAt())
                        .isNewAddition(false)
                        .build())
                .toList();
    }
}