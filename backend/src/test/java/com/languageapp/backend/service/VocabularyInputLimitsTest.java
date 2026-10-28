package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.DynamicPracticeSubmitRequest;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VocabularyInputLimitsTest {
    @Mock Clock clock;
    @Mock StudentVocabularyRepository vocabulary;
    @Mock UserRepository users;
    @Mock StreakService streak;
    @Mock AchievementService achievements;
    @Mock VocabularyLookupStore lookupStore;
    @Mock TranslationClient translations;
    @InjectMocks VocabularyService service;
    @Mock DynamicPracticeAttemptRepository attempts;
    @InjectMocks DynamicPracticeService practice;

    @Test void validatesAndLooksUpBatchWordsWithTheSameLocaleIndependentNormalization() {
        when(lookupStore.snapshot(eq("test@example.test"),anyList())).thenReturn(new VocabularyLookupStore.Snapshot(
                UUID.randomUUID(),UUID.randomUUID(),"en",Map.of("i",com.languageapp.backend.dto.response.VocabularyResponse.builder().word("i").build())));
        Locale previous=Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR"));service.addKnownWordsBatch("test@example.test",List.of("I")); }
        finally { Locale.setDefault(previous); }
        verify(lookupStore).snapshot("test@example.test",List.of("i"));
        verifyNoInteractions(translations);

    }

    @Test void rejectsAnOversizedBatchBeforeAnyLookupOrTranslation() {
        assertThrows(BadRequestException.class,()->service.addKnownWordsBatch("test@example.test",Collections.nCopies(1001,"apple")));
        verifyNoInteractions(vocabulary,users,streak,achievements,lookupStore,translations);
    }
    @Test void validatesEveryWordIncludingCaseExpansionBeforeStartingTheBatch() {
        assertThrows(BadRequestException.class,()->service.addKnownWordsBatch("test@example.test",List.of("apple","İ".repeat(100))));
        verifyNoInteractions(vocabulary,users,streak,achievements,lookupStore,translations);
    }
    @Test void boundsLookupAndSourceBeforeAnyExternalCall() {
        assertThrows(BadRequestException.class,()->service.lookupAndSaveWord("test@example.test","x".repeat(101),"source"));
        assertThrows(BadRequestException.class,()->service.lookupAndSaveWord("test@example.test","apple","x".repeat(51)));
        verifyNoInteractions(vocabulary,users,streak,achievements,lookupStore,translations);
    }
    @Test void boundsDynamicResultListsAndRawAnswersBeforeRewardOrDatabaseWork() {
        var answer=new DynamicPracticeSubmitRequest.AnswerResult();answer.setExerciseId(UUID.randomUUID());answer.setAnswer("apple");
        var request=new DynamicPracticeSubmitRequest();request.setAttemptId(UUID.randomUUID());request.setResults(Collections.nCopies(1001,answer));
        assertThrows(BadRequestException.class,()->practice.submit("test@example.test",request));
        request.setResults(List.of(answer));answer.setAnswer("x".repeat(10001));
        assertThrows(BadRequestException.class,()->practice.submit("test@example.test",request));
        verifyNoInteractions(vocabulary,users,streak,achievements,lookupStore,translations);
    }
}
