package com.languageapp.backend.service;

import com.languageapp.backend.entity.Lesson;
import com.languageapp.backend.entity.Result;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.DifficultyLevel;
import com.languageapp.backend.repository.ResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDifficultyCalculatorTest {

    @Mock private ResultRepository resultRepository;

    @InjectMocks
    private UserDifficultyCalculator calculator;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId(UUID.randomUUID());
        user.setPreferredDifficulty(DifficultyLevel.DYNAMIC); // Default az adaptív rendszer
    }

    // --- 1. TESZT: Manuális felülírás ---
    @Test
    void determineTargetDifficulty_ManualOverride_ReturnsPreferred() {
        // Arrange
        user.setPreferredDifficulty(DifficultyLevel.HARD); // A diák fixálta a szintet

        // Act
        String diff = calculator.determineTargetDifficulty(user);

        // Assert
        assertEquals("HARD", diff, "Ha nem DYNAMIC, a beállított fix szintet kell visszaadnia.");
    }

    // --- 2. TESZT: Új felhasználó előzmények nélkül ---
    @Test
    void determineTargetDifficulty_NoHistory_ReturnsEasy() {
        // Arrange
        when(resultRepository.findTop5ByUserUserIdOrderBySubmittedAtDesc(user.getUserId())).thenReturn(List.of());

        // Act
        String diff = calculator.determineTargetDifficulty(user);

        // Assert
        assertEquals("EASY", diff, "Előzmények nélkül az alapértelmezett szint az EASY.");
    }

    // --- 3. TESZT: Visszaesés védelem (Medium -> Easy azonnali lefokozás rossz eredménynél) ---
    @Test
    void determineTargetDifficulty_MediumScoreDropsBelow50_DemotesToEasy() {
        // Arrange
        Lesson mediumLesson = new Lesson();
        mediumLesson.setDifficulty("MEDIUM");

        Result r1 = new Result(); r1.setLesson(mediumLesson); r1.setScore(40); // Nagyon rossz utolsó eredmény
        Result r2 = new Result(); r2.setLesson(mediumLesson); r2.setScore(45);

        when(resultRepository.findTop5ByUserUserIdOrderBySubmittedAtDesc(user.getUserId())).thenReturn(List.of(r1, r2));
        when(resultRepository.countByUserUserId(user.getUserId())).thenReturn(10L); // Már csinált 10 leckét

        // Act
        String diff = calculator.determineTargetDifficulty(user);

        // Assert
        assertEquals("EASY", diff, "Ha a WMA 50% alá esik MEDIUM-on, azonnal EASY-re kell degradálni.");
    }

    // --- 4. TESZT: Szintlépés (Easy -> Medium ha az eredmények jók és a pacing megvan) ---
    @Test
    void determineTargetDifficulty_EasyWithHighWma_PromotesToMedium() {
        // Arrange
        Lesson easyLesson = new Lesson();
        easyLesson.setDifficulty("EASY");

        Result r1 = new Result(); r1.setLesson(easyLesson); r1.setScore(100);
        Result r2 = new Result(); r2.setLesson(easyLesson); r2.setScore(90);

        when(resultRepository.findTop5ByUserUserIdOrderBySubmittedAtDesc(user.getUserId())).thenReturn(List.of(r1, r2));
        // A pacing szabály szerint minimum 3 leckét meg kell csinálni a szintlépéshez
        when(resultRepository.countByUserUserId(user.getUserId())).thenReturn(3L);

        // Act
        String diff = calculator.determineTargetDifficulty(user);

        // Assert
        assertEquals("MEDIUM", diff, "75% feletti WMA és >= 3 próbálkozás esetén MEDIUM-ra kell léptetni.");
    }
}