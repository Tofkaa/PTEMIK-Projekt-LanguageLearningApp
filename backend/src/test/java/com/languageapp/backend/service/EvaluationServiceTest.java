package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.ExerciseSubmission;
import com.languageapp.backend.dto.request.LessonSubmitRequest;
import com.languageapp.backend.dto.response.ExerciseCheckResponse;
import com.languageapp.backend.dto.response.MistakeDTO;
import com.languageapp.backend.entity.Exercise;
import com.languageapp.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private ResultRepository resultRepository;
    @Mock private ProgressRepository progressRepository;
    @Mock private UserDifficultyCalculator userDifficultyCalculator;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private AchievementService achievementService;
    @Mock private ChallengeRepository challengeRepository;
    @Mock private ChallengeService challengeService;
    @Mock private AssignmentSessionRepository sessionRepository;
    @Mock private StreakService streakService;

    @InjectMocks
    private EvaluationService evaluationService;

    private Exercise testExercise;

    @BeforeEach
    void setUp() {
        testExercise = new Exercise();
        testExercise.setExerciseId(UUID.randomUUID());
        testExercise.setType("TYPING");
        // Beállítjuk a helyes választ a Mock feladathoz
        testExercise.setCorrectAnswer(Map.of("answer", "kutya"));
    }

    // --- 1. TESZT: Tökéletes válasz azonnali ellenőrzésnél ---
    @Test
    void checkSingleExercise_PerfectMatch_ReturnsCorrect() {
        // Arrange
        when(exerciseRepository.findById(testExercise.getExerciseId())).thenReturn(Optional.of(testExercise));

        // Act
        ExerciseCheckResponse response = evaluationService.checkSingleExercise(testExercise.getExerciseId(), "kutya");

        // Assert
        assertTrue(response.isCorrect(), "A tökéletes válasznak helyesnek kell lennie.");
        assertFalse(response.isAlmostCorrect(), "Nem lehet 'majdnem jó', ha tökéletes.");
    }

    // --- 2. TESZT: Elgépelés (Levenshtein algoritmus) detektálása ---
    @Test
    void checkSingleExercise_Typo_ReturnsAlmostCorrect() {
        // Arrange
        when(exerciseRepository.findById(testExercise.getExerciseId())).thenReturn(Optional.of(testExercise));

        // Act: Kifelejtünk egy betűt (kutya -> kuty)
        ExerciseCheckResponse response = evaluationService.checkSingleExercise(testExercise.getExerciseId(), "kuty");

        // Assert
        assertFalse(response.isCorrect(), "Az elgépelt válasz nem lehet 100%-ig helyes.");
        assertTrue(response.isAlmostCorrect(), "A Levenshtein algoritmusnak fel kell ismernie az apró elgépelést.");
    }

    // --- 3. TESZT: Teljesen rossz válasz ---
    @Test
    void checkSingleExercise_WrongAnswer_ReturnsIncorrect() {
        // Arrange
        when(exerciseRepository.findById(testExercise.getExerciseId())).thenReturn(Optional.of(testExercise));

        // Act
        ExerciseCheckResponse response = evaluationService.checkSingleExercise(testExercise.getExerciseId(), "macska");

        // Assert
        assertFalse(response.isCorrect());
        assertFalse(response.isAlmostCorrect());
    }

    // --- 4. TESZT: XP Kalkuláció - Elsőre hibátlan (10 XP) ---
    @Test
    void calculateEvaluationDetails_AllCorrectFirstTry_GrantsFullXp() {
        // Arrange
        ExerciseSubmission submission = new ExerciseSubmission();
        submission.setExerciseId(testExercise.getExerciseId());
        submission.setAnswer("kutya");
        submission.setRetry(false); // Első próbálkozás

        LessonSubmitRequest request = new LessonSubmitRequest();
        request.setAnswers(List.of(submission));

        List<MistakeDTO> mistakes = new ArrayList<>();

        // Act
        EvaluationService.EvaluationDetails details = evaluationService.calculateEvaluationDetails(List.of(testExercise), request, mistakes);

        // Assert
        assertEquals(1, details.getCorrectCount(), "1 helyes válasz kell legyen.");
        assertEquals(10, details.getPotentialXp(), "Elsőre hibátlan válaszért 10 XP jár.");
        assertTrue(mistakes.isEmpty(), "Nem lehetnek rögzített hibák.");
    }

    // --- 5. TESZT: XP Kalkuláció - Elsőre rossz, második eséllyel javítva (5 XP) ---
    @Test
    void calculateEvaluationDetails_WithRetry_GrantsHalfXp() {
        // Arrange
        // 1. Rossz próbálkozás
        ExerciseSubmission firstAttempt = new ExerciseSubmission();
        firstAttempt.setExerciseId(testExercise.getExerciseId());
        firstAttempt.setAnswer("macska");
        firstAttempt.setRetry(false);

        // 2. Második (javított) próbálkozás
        ExerciseSubmission retryAttempt = new ExerciseSubmission();
        retryAttempt.setExerciseId(testExercise.getExerciseId());
        retryAttempt.setAnswer("kutya");
        retryAttempt.setRetry(true); // Ez egy második esély!

        LessonSubmitRequest request = new LessonSubmitRequest();
        request.setAnswers(List.of(firstAttempt, retryAttempt));

        List<MistakeDTO> mistakes = new ArrayList<>();

        // Act
        EvaluationService.EvaluationDetails details = evaluationService.calculateEvaluationDetails(List.of(testExercise), request, mistakes);

        // Assert
        assertEquals(1, details.getCorrectCount(), "A feladat végül sikeres lett a javítás miatt.");
        assertEquals(5, details.getPotentialXp(), "Mivel csak második eséllyel sikerült, fele XP (5) jár.");
        assertEquals(1, mistakes.size(), "A rendszernek rögzítenie kell az eredeti hibát a statisztikához.");
    }
}