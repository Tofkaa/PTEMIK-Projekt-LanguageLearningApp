package com.languageapp.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.languageapp.backend.dto.response.DynamicExerciseDTO;
import com.languageapp.backend.dto.response.VocabularyDetailDTO;
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
import java.util.*;
import java.util.stream.Collectors;

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
     * External API Call for fetching translation from MyMemory API
     */
    private String fetchTranslationFromExternalApi(String word) {
        try {
            String cleanWord = word.trim().toLowerCase();
            String url = "https://api.mymemory.translated.net/get?q={word}&langpair=en|hu";

            // optinal for higher rate limit
            // String url = "https://api.mymemory.translated.net/get?q={word}&langpair=en|hu&de=email@gmail.com";

            String response = restTemplate.getForObject(url, String.class, cleanWord);

            JsonNode root = objectMapper.readTree(response);

            String translatedText = root.path("responseData").path("translatedText").asText();

            if (translatedText != null && !translatedText.isEmpty() && !translatedText.contains("MYMEMORY WARNING")) {
                return translatedText;
            } else {
                return "Ismeretlen jelentés";
            }

        } catch (Exception e) {
            log.error("Hiba a külső szótár API hívásakor a '{}' szóra: {}", word, e.getMessage());
            return "Fordítás nem érhető el";
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

    /**
     * Returns the entire student dictionary as a key-value (word -> SRS level) map.
     * This optimizes frontend rendering, avoiding unnecessary API calls.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> getVocabularyMap(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<StudentVocabulary> allVocabs = vocabularyRepository
                .findAllByUser_UserIdOrderByFirstSeenAtDesc(user.getUserId());

        return allVocabs.stream()
                .filter(voc -> voc.getSrsLevel() >= 3)
                .collect(Collectors.toMap(
                        voc -> voc.getWord().toLowerCase(),
                        StudentVocabulary::getSrsLevel,
                        (existing, replacement) -> existing
                ));
    }

    /**
     * Visszaadja a diák összes mentett szavát részletes SRS adatokkal a Vocabulary Hub számára.
     */
    @Transactional(readOnly = true)
    public List<VocabularyDetailDTO> getDetailedVocabularyForUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<StudentVocabulary> entries = vocabularyRepository.findAllByUser(user);

        return entries.stream().map(v -> new VocabularyDetailDTO(
                v.getWord(),
                v.getTranslation(),
                v.getSrsLevel(),
                v.getNextPracticeAt(),
                v.getSrsLevel() >= 4
        )).toList();
    }

    /**
     * Dinamikus gyakorló feladatsor generálása az esedékes (due) vagy alacsony SRS szintű szavakból.
     */
    @Transactional(readOnly = true)
    public List<DynamicExerciseDTO> generateDynamicPracticeSession(String email, int limit) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalDateTime now = LocalDateTime.now();

        // 1. Megkeressük azokat a szavakat, amik esedékesek a gyakorlásra, vagy még alacsony szintűek
        List<StudentVocabulary> dueWords = vocabularyRepository.findDueOrLowLevelWords(user.getUserId(), now);

        if (dueWords.isEmpty()) {
            // Fallback: Ha nincs esedékes, véletlenszerűen szedünk fel eddig tanult szavakat
            dueWords = vocabularyRepository.findAllByUser(user);
            Collections.shuffle(dueWords);
        }

        // Limitáljuk a méretet (pl. max 10-15 feladat egy sessionben)
        if (dueWords.size() > limit) {
            dueWords = dueWords.subList(0, limit);
        }

        List<StudentVocabulary> allUserWords = vocabularyRepository.findAllByUser(user);
        List<DynamicExerciseDTO> dynamicExercises = new ArrayList<>();

        for (StudentVocabulary item : dueWords) {
            // Véletlenszerűen kiválasztunk egy feladattípust erre a szóra: 0 = Multiple Choice, 1 = Word Bank, 2 = Translation
            int exerciseTypeSelector = new Random().nextInt(3);

            if (exerciseTypeSelector == 0) {
                // MULTIPLE CHOICE GENERÁLÁS
                List<String> wrongOptions = allUserWords.stream()
                        .filter(w -> !w.getWord().equalsIgnoreCase(item.getWord()))
                        .map(StudentVocabulary::getTranslation)
                        .distinct()
                        .collect(Collectors.toList());
                Collections.shuffle(wrongOptions);

                List<String> options = new ArrayList<>();
                options.add(item.getTranslation());
                if (wrongOptions.size() >= 3) {
                    options.addAll(wrongOptions.subList(0, 3));
                } else {
                    options.addAll(wrongOptions);
                    options.add("alternatív jelentés");
                }
                Collections.shuffle(options);

                dynamicExercises.add(DynamicExerciseDTO.builder()
                        .exerciseId(UUID.randomUUID())
                        .type("MULTIPLE_CHOICE")
                        .question("Mi a jelentése ennek a szónak: " + item.getWord() + "?")
                        .targetWord(item.getWord())
                        .correctAnswer(item.getTranslation())
                        .options(options)
                        .build());

            } else if (exerciseTypeSelector == 1 && item.getWord().contains(" ")) {
                // WORD BANK (ha több szóból álló kifejezés)
                List<String> words = Arrays.asList(item.getWord().split(" "));
                List<String> shuffledWords = new ArrayList<>(words);
                Collections.shuffle(shuffledWords);

                dynamicExercises.add(DynamicExerciseDTO.builder()
                        .exerciseId(UUID.randomUUID())
                        .type("WORD_BANK")
                        .question("Rakd sorba a kifejezés szavait: " + item.getTranslation())
                        .targetWord(item.getWord())
                        .correctAnswer(item.getWord())
                        .options(shuffledWords)
                        .build());

            } else {
                // TRANSLATION (Gépeléses)
                dynamicExercises.add(DynamicExerciseDTO.builder()
                        .exerciseId(UUID.randomUUID())
                        .type("TRANSLATION")
                        .question("Fordítsd le magyarra: " + item.getWord())
                        .targetWord(item.getWord())
                        .correctAnswer(item.getTranslation())
                        .hint(item.getWord() + " = " + item.getTranslation())
                        .build());
            }
        }

        return dynamicExercises;
    }
}