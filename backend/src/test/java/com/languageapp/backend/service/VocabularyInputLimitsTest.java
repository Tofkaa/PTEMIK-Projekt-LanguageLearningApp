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
    @InjectMocks VocabularyService service;

    @Test void validatesAndLooksUpBatchWordsWithTheSameLocaleIndependentNormalization() {
        var course=new com.languageapp.backend.entity.Course();course.setCourseId(UUID.randomUUID());
        var user=new com.languageapp.backend.entity.User();user.setUserId(UUID.randomUUID());user.setActiveCourse(course);
        when(users.findByEmail("test@example.test")).thenReturn(Optional.of(user));
        when(vocabulary.findByUser_UserIdAndCourse_CourseIdAndWordIgnoreCase(any(),any(),anyString()))
                .thenReturn(Optional.of(new com.languageapp.backend.entity.StudentVocabulary()));
        Locale previous=Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR"));service.addKnownWordsBatch("test@example.test",List.of("I")); }
        finally { Locale.setDefault(previous); }
        verify(vocabulary).findByUser_UserIdAndCourse_CourseIdAndWordIgnoreCase(user.getUserId(),course.getCourseId(),"i");
        verify(vocabulary,never()).save(any());
    }

    @Test void rejectsAnOversizedBatchBeforeAnyLookupOrTranslation() {
        assertThrows(BadRequestException.class,()->service.addKnownWordsBatch("test@example.test",Collections.nCopies(1001,"apple")));
        verifyNoInteractions(vocabulary,users,streak,achievements);
    }
    @Test void validatesEveryWordIncludingCaseExpansionBeforeStartingTheBatch() {
        assertThrows(BadRequestException.class,()->service.addKnownWordsBatch("test@example.test",List.of("apple","İ".repeat(100))));
        verifyNoInteractions(vocabulary,users,streak,achievements);
    }
    @Test void boundsLookupAndSourceBeforeAnyExternalCall() {
        assertThrows(BadRequestException.class,()->service.lookupAndSaveWord("test@example.test","x".repeat(101),"source"));
        assertThrows(BadRequestException.class,()->service.lookupAndSaveWord("test@example.test","apple","x".repeat(51)));
        verifyNoInteractions(vocabulary,users,streak,achievements);
    }
    @Test void boundsDynamicResultListsAndNestedWordsBeforeRewardOrDatabaseWork() {
        var answer=new DynamicPracticeSubmitRequest.AnswerResult();answer.setTargetWord("apple");
        var request=new DynamicPracticeSubmitRequest();request.setResults(Collections.nCopies(1001,answer));
        assertThrows(BadRequestException.class,()->service.processDynamicPracticeSession("test@example.test",request));
        request.setResults(List.of(answer));answer.setTargetWord("x".repeat(101));
        assertThrows(BadRequestException.class,()->service.processDynamicPracticeSession("test@example.test",request));
        verifyNoInteractions(vocabulary,users,streak,achievements);
    }
}
