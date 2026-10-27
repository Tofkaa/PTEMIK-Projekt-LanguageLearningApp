package com.languageapp.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.languageapp.backend.dto.request.DynamicPracticeSubmitRequest;
import com.languageapp.backend.dto.response.DynamicExerciseDTO;
import com.languageapp.backend.dto.response.VocabularyDetailDTO;
import com.languageapp.backend.dto.response.VocabularyResponse;
import com.languageapp.backend.entity.Course;
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
import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyService {
    private final Clock clock;

    private final StudentVocabularyRepository vocabularyRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StreakService streakService;
    private final AchievementService achievementService;

    @Transactional
    public VocabularyResponse lookupAndSaveWord(String email, String word, String source) {
        com.languageapp.backend.validation.AnswerLimits.requireWord(word);
        String cleanWord = word.trim().toLowerCase(Locale.ROOT);
        com.languageapp.backend.validation.AnswerLimits.requireWord(cleanWord);
        if (source != null && source.length() > 50)
            throw new com.languageapp.backend.exception.BadRequestException("A szótári forrás legfeljebb 50 karakter lehet.");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Fetch the user's currently active course for multi-language support
        Course activeCourse = user.getActiveCourse();

        // Check if the word already exists in the vocabulary specific to the user's active course
        Optional<StudentVocabulary> existing = vocabularyRepository.findByUser_UserIdAndCourse_CourseIdAndWordIgnoreCase(user.getUserId(), activeCourse.getCourseId(), cleanWord);

        if (existing.isPresent()) {
            StudentVocabulary voc = existing.get();
            return buildVocabularyResponse(voc, false);
        }

        // Fetch translation dynamically using the active course's language code (e.g., 'es', 'de')
        String translation = fetchTranslationFromExternalApi(cleanWord, activeCourse.getLanguageCode());

        StudentVocabulary newVoc = new StudentVocabulary();
        newVoc.setUser(user);

        // Associate the newly saved word with the user's active course
        newVoc.setCourse(activeCourse);
        newVoc.setWord(cleanWord);
        newVoc.setTranslation(translation);
        newVoc.setSource(source != null ? source : "DICTIONARY");

        newVoc = vocabularyRepository.save(newVoc);

        log.info("New word '{}' added to user {}'s {} vocabulary", cleanWord, email, activeCourse.getLanguageCode());

        achievementService.checkAndAwardAchievements(user, -1);
        return buildVocabularyResponse(newVoc, true);
    }

    /**
     * External API Call for fetching translation from MyMemory API.
     * Dynamically injects the target course's language code for correct translation pairing.
     */
    private String fetchTranslationFromExternalApi(String word, String courseCode) {
        try {
            String cleanWord = word.trim().toLowerCase(Locale.ROOT);

            // Construct the dynamic URL using the courseCode variable
            String url = "https://api.mymemory.translated.net/get?q={word}&langpair=" + courseCode + "|hu";

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

        voc.setLastPracticedAt(clock.instant());

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

        voc.setNextPracticeAt(clock.instant().plus(daysToAdd, ChronoUnit.DAYS));
        StudentVocabulary updatedVoc = vocabularyRepository.save(voc);

        achievementService.checkAndAwardAchievements(voc.getUser(), -1);
        return buildVocabularyResponse(updatedVoc, false);
    }

    /**
     * Retrieves the words that the student needs to repeat up to the current time.
     * Results are in ascending order of next practice time (oldest due first).
     * Now strictly filtered by the user's actively selected language course.
     */
    @Transactional(readOnly = true)
    public List<VocabularyResponse> getDueVocabularyForToday(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Instant now = clock.instant();

        // Query due vocabulary specifically isolated to the active course ID
        List<StudentVocabulary> dueVocabs = vocabularyRepository
                .findAllByUser_UserIdAndCourse_CourseIdAndNextPracticeAtLessThanEqualOrderByNextPracticeAtAsc(user.getUserId(), user.getActiveCourse().getCourseId(), now);

        return dueVocabs.stream()
                .map(voc -> buildVocabularyResponse(voc, false))
                .toList();
    }

    /**
     * Returns the entire student dictionary as a key-value (word -> SRS level) map.
     * This optimizes frontend rendering, avoiding unnecessary API calls.
     * Generates the map exclusively from words belonging to the currently active course.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> getVocabularyMap(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Fetch words tied to the currently active course
        List<StudentVocabulary> allVocabs = vocabularyRepository
                .findAllByUser_UserIdAndCourse_CourseIdOrderByFirstSeenAtDesc(user.getUserId(), user.getActiveCourse().getCourseId());

        return allVocabs.stream()
                .filter(voc -> voc.getSrsLevel() >= 3)
                .collect(Collectors.toMap(
                        voc -> voc.getWord().toLowerCase(),
                        StudentVocabulary::getSrsLevel,
                        (existing, replacement) -> existing
                ));
    }

    /**
     * Returns all of the student's saved words with detailed SRS data for Vocabulary Hub.
     * Added course-level filtering.
     */
    @Transactional(readOnly = true)
    public List<VocabularyDetailDTO> getDetailedVocabularyForUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Fetch dictionary entries specifically for the active course
        List<StudentVocabulary> entries = vocabularyRepository.findAllByUserAndCourse(user, user.getActiveCourse());

        return entries.stream().map(v -> new VocabularyDetailDTO(
                v.getWord(),
                v.getTranslation(),
                v.getSrsLevel(),
                v.getNextPracticeAt(),
                v.getSrsLevel() >= 4
        )).toList();
    }

    /**
     * Add words to user's vocabulary where they did not request the clickabletext help and still got the answer right.
     * This means the user presumably already knew the meaning well enough to grant them a srs level of 2.
     * The batch process now respects the multi-language boundary.
     */
    @Transactional
    public void addKnownWordsBatch(String email, List<String> words) {
        if (words == null || words.isEmpty()) return;
        com.languageapp.backend.validation.AnswerLimits.requireWords(words);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Establish context of the current active language course
        Course activeCourse = user.getActiveCourse();

        for (String word : words) {
            String cleanWord = word.trim().toLowerCase(Locale.ROOT);

            // Prevent duplicates within the same language course
            if (vocabularyRepository.findByUser_UserIdAndCourse_CourseIdAndWordIgnoreCase(user.getUserId(), activeCourse.getCourseId(), cleanWord).isPresent()) {
                continue;
            }

            String translation = fetchTranslationFromExternalApi(cleanWord, activeCourse.getLanguageCode());

            StudentVocabulary newVoc = new StudentVocabulary();
            newVoc.setUser(user);
            newVoc.setCourse(activeCourse); // Link to course
            newVoc.setWord(cleanWord);
            newVoc.setTranslation(translation);
            newVoc.setSource("AUTO_LEARNED");
            newVoc.setSrsLevel(2);
            newVoc.setNextPracticeAt(clock.instant().plus(3, ChronoUnit.DAYS));
            newVoc.setLastPracticedAt(clock.instant());

            vocabularyRepository.save(newVoc);
        }
        achievementService.checkAndAwardAchievements(user, -1);
    }

    /**
     * Generate Dynamic Practice session from due words in the users vocabulary.
     * Exercises and multiple-choice distractors are generated exclusively from the active course's word pool.
     */
    @Transactional(readOnly = true)
    public List<DynamicExerciseDTO> generateDynamicPracticeSession(String email, int limit) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Instant now = clock.instant();
        UUID courseId = user.getActiveCourse().getCourseId();

        // Fetch due words only from the currently active course
        List<StudentVocabulary> dueWords = vocabularyRepository
                .findAllByUser_UserIdAndCourse_CourseIdAndNextPracticeAtLessThanEqualOrderByNextPracticeAtAsc(user.getUserId(), courseId, now);

        if (dueWords.size() > limit) {
            dueWords = dueWords.subList(0, limit);
        }

        // Fetch all words from the active course to serve as wrong options (distractors)
        List<StudentVocabulary> allCourseWords = vocabularyRepository.findAllByUserAndCourse(user, user.getActiveCourse());
        List<DynamicExerciseDTO> dynamicExercises = new ArrayList<>();

        for (StudentVocabulary item : dueWords) {
            int exerciseTypeSelector = new Random().nextInt(3);

            if (exerciseTypeSelector == 0) {

                List<String> wrongOptions = allCourseWords.stream()
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
                        .questionLang("hu").answerLang("hu")
                        .questionParts(List.of(new DynamicExerciseDTO.LanguagePart("Mi a jelentése ennek a szónak: ", "hu"),
                                new DynamicExerciseDTO.LanguagePart(item.getWord(), user.getActiveCourse().getLanguageCode()),
                                new DynamicExerciseDTO.LanguagePart("?", "hu")))
                        .targetWord(item.getWord())
                        .correctAnswer(item.getTranslation())
                        .options(options)
                        .build());

            } else if (exerciseTypeSelector == 1 && item.getWord().contains(" ")) {
                List<String> words = Arrays.asList(item.getWord().split(" "));
                List<String> shuffledWords = new ArrayList<>(words);
                Collections.shuffle(shuffledWords);

                dynamicExercises.add(DynamicExerciseDTO.builder()
                        .exerciseId(UUID.randomUUID())
                        .type("WORD_BANK")
                        .question("Rakd sorba a kifejezés szavait: " + item.getTranslation())
                        .questionLang("hu").answerLang(user.getActiveCourse().getLanguageCode())
                        .targetWord(item.getWord())
                        .correctAnswer(item.getWord())
                        .options(shuffledWords)
                        .build());

            } else {
                dynamicExercises.add(DynamicExerciseDTO.builder()
                        .exerciseId(UUID.randomUUID())
                        .type("TRANSLATION")
                        .question("Fordítsd le magyarra: " + item.getWord())
                        .questionLang("hu").answerLang("hu")
                        .questionParts(List.of(new DynamicExerciseDTO.LanguagePart("Fordítsd le magyarra: ", "hu"),
                                new DynamicExerciseDTO.LanguagePart(item.getWord(), user.getActiveCourse().getLanguageCode())))
                        .targetWord(item.getWord())
                        .correctAnswer(item.getTranslation())
                        .hint(item.getWord() + " = " + item.getTranslation())
                        .build());
            }
        }

        return dynamicExercises;
    }

    /**
     * Processes practice session submissions, ensuring evaluations and SRS updates
     * are strictly applied to words belonging to the active course context.
     */
    @Transactional
    public Map<String, Object> processDynamicPracticeSession(String email, DynamicPracticeSubmitRequest request) {
        var results = request.getResults();
        if (results == null || results.size() > com.languageapp.backend.validation.AnswerLimits.MAX_WORDS || request.getTimeTakenSeconds() < 0)
            throw new com.languageapp.backend.exception.BadRequestException("Érvénytelen vagy túl nagy gyakorlási eredmény.");
        for (var result : results) {
            if (result == null) throw new com.languageapp.backend.exception.BadRequestException("Hiányzó gyakorlási eredmény.");
            com.languageapp.backend.validation.AnswerLimits.requireWord(result.getTargetWord());
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        int correctCount = 0;
        int totalQuestions = request.getResults().size();
        int earnedXp = 0;

        // Ensure words are updated within the correct language course context
        UUID courseId = user.getActiveCourse().getCourseId();

        for (DynamicPracticeSubmitRequest.AnswerResult result : request.getResults()) {

            // Search for the submitted word specifically within the active course's vocabulary
            Optional<StudentVocabulary> vocOpt = vocabularyRepository
                    .findByUser_UserIdAndCourse_CourseIdAndWordIgnoreCase(user.getUserId(), courseId, result.getTargetWord());

            if (vocOpt.isPresent()) {
                StudentVocabulary voc = vocOpt.get();
                voc.setLastPracticedAt(clock.instant());

                // Anti-Cheat protection: if the user manages to bypass the systems intended usage functions and practice ahead of time
                // meaning the due date is in the future, we ignore it and do not update their srs level
                if (voc.getNextPracticeAt() != null && voc.getNextPracticeAt().isAfter(clock.instant())) {
                    log.warn("User {} tried to practice word '{}' ahead of time. Ignoring SRS update.", email, voc.getWord());
                    continue;
                }

                voc.setLastPracticedAt(clock.instant());
                if (result.isCorrect()) {
                    correctCount++;
                    earnedXp += 2;
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

                voc.setNextPracticeAt(clock.instant().plus(daysToAdd, ChronoUnit.DAYS));
                vocabularyRepository.save(voc);
            }
        }

        if (correctCount == totalQuestions && totalQuestions > 0) {
            earnedXp += 10;
        }

        user.setXp(user.getXp() + earnedXp);
        streakService.updateActivity(user);
        userRepository.save(user);

        var newAchievements = achievementService.checkAndAwardAchievements(user, (correctCount * 100) / (totalQuestions == 0 ? 1 : totalQuestions));

        return Map.of(
                "newAchievements", newAchievements,
                "correctCount", correctCount,
                "totalQuestions", totalQuestions,
                "earnedXp", earnedXp,
                "newStreak", user.getStreak()
        );
    }

    /**
     * Helper method to map StudentVocabulary to VocabularyResponse
     */
    private VocabularyResponse buildVocabularyResponse(StudentVocabulary voc, boolean isNewAddition) {
        return VocabularyResponse.builder()
                .vocabularyId(voc.getVocabularyId())
                .word(voc.getWord())
                .translation(voc.getTranslation())
                .srsLevel(voc.getSrsLevel())
                .nextPracticeAt(voc.getNextPracticeAt())
                .isNewAddition(isNewAddition)
                .build();
    }
}
