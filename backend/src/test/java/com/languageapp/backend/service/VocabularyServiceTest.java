package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.VocabularyResponse;
import com.languageapp.backend.entity.StudentVocabulary;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.ForbiddenException;
import com.languageapp.backend.repository.StudentVocabularyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VocabularyServiceTest {

    @Mock
    private StudentVocabularyRepository vocabularyRepository;

    @InjectMocks
    private VocabularyService vocabularyService;

    private User testUser;
    private StudentVocabulary testVocabulary;
    private final UUID VOCAB_ID = UUID.randomUUID();
    private final String USER_EMAIL = "vau@gmail.com";

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail(USER_EMAIL);
        testUser.setUserId(UUID.randomUUID());

        testVocabulary = new StudentVocabulary();
        testVocabulary.setVocabularyId(VOCAB_ID);
        testVocabulary.setUser(testUser);
        testVocabulary.setWord("apple");
        testVocabulary.setTranslation("alma");
        testVocabulary.setSrsLevel(0);
        testVocabulary.setNextPracticeAt(LocalDateTime.now());
    }

    @Test
    void recordPracticeResult_CorrectAnswer_ShouldIncreaseLevelAndAdvanceDate() {
        // Arrange
        when(vocabularyRepository.findById(VOCAB_ID)).thenReturn(Optional.of(testVocabulary));
        when(vocabularyRepository.save(any(StudentVocabulary.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        VocabularyResponse response = vocabularyService.recordPracticeResult(VOCAB_ID, USER_EMAIL, true);

        // Assert
        assertEquals(1, response.getSrsLevel(), "Az SRS szintnek 1-re kell nőnie");
        assertTrue(response.getNextPracticeAt().isAfter(LocalDateTime.now().plusHours(23)),
                "A következő gyakorlásnak kb. 1 nap múlva kell lennie");
        verify(vocabularyRepository, times(1)).save(testVocabulary);
    }

    @Test
    void recordPracticeResult_IncorrectAnswer_ShouldResetLevel() {
        // Arrange
        testVocabulary.setSrsLevel(3); // Már egy haladó szinten van
        when(vocabularyRepository.findById(VOCAB_ID)).thenReturn(Optional.of(testVocabulary));
        when(vocabularyRepository.save(any(StudentVocabulary.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        VocabularyResponse response = vocabularyService.recordPracticeResult(VOCAB_ID, USER_EMAIL, false);

        // Assert
        assertEquals(0, response.getSrsLevel(), "Hibás válasz esetén a szintnek le kell nullázódnia");
        // Mivel 0-s szintnél 0 napot adunk hozzá, a nextPracticeAt a jelenlegi időponthoz nagyon közeli kell legyen
        assertTrue(response.getNextPracticeAt().isBefore(LocalDateTime.now().plusMinutes(1)));
    }

    @Test
    void recordPracticeResult_WrongUser_ShouldThrowForbiddenException() {
        // Arrange
        when(vocabularyRepository.findById(VOCAB_ID)).thenReturn(Optional.of(testVocabulary));

        // Act & Assert
        assertThrows(ForbiddenException.class, () -> {
            vocabularyService.recordPracticeResult(VOCAB_ID, "hacker@gmail.com", true);
        });

        // Biztosítjuk, hogy illetéktelen hívásnál soha ne hívódjon meg a mentés
        verify(vocabularyRepository, never()).save(any());
    }
}