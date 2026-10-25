package com.languageapp.backend.service;

import com.languageapp.backend.entity.*;
import com.languageapp.backend.repository.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MasteryServiceTest {
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final ProgressRepository progress = mock(ProgressRepository.class);
    private final MasteryService service = new MasteryService(lessons, progress);
    private Lesson lesson;
    private LessonTopic topic;

    @BeforeEach void setup() {
        topic = new LessonTopic(); topic.setTopicId(UUID.randomUUID());
        lesson = new Lesson(); lesson.setLessonId(UUID.randomUUID()); lesson.setTopic(topic);
        lesson.setExercises(List.of(exercise("EASY", true), exercise("MEDIUM", true), exercise("HARD", false)));
    }
    private Exercise exercise(String level, boolean active) {
        Exercise e = new Exercise(); e.setDifficulty(level); e.setActive(active); return e;
    }
    private Progress pass(String level, int score) {
        Progress p = new Progress(); p.setLesson(lesson); p.setCompletedDifficulty(level);
        p.setIsCompleted(true); p.setHighestScore(score); return p;
    }
    @Test void successfulImperfectPassesCountAndInactiveLevelIsNotRequired() {
        assertEquals(List.of("EASY", "MEDIUM"), service.availableLevels(lesson));
        assertTrue(service.isMastered(lesson, List.of(pass("EASY", 60), pass("MEDIUM", 75))));
    }
    @Test void incompleteAndFailedLevelsDoNotCount() {
        Progress failed = pass("MEDIUM", 40); failed.setIsCompleted(false);
        assertFalse(service.isMastered(lesson, List.of(pass("EASY", 80), failed)));
        assertEquals(List.of("EASY"), service.completedLevels(lesson, List.of(pass("EASY", 80), failed)));
    }
    @Test void noContentDoesNotGrantVacuousMastery() {
        lesson.setExercises(List.of());
        assertFalse(service.isMastered(lesson, List.of()));
        assertFalse(service.isTopicMastered(topic.getTopicId(), List.of(lesson), List.of()));
    }
    @Test void topicRequiresEveryActiveLessonButIgnoresEmptyAndSuspendedLessons() {
        Lesson empty = new Lesson(); empty.setTopic(topic); empty.setLessonId(UUID.randomUUID());
        Lesson suspended = new Lesson(); suspended.setTopic(topic); suspended.setActive(false);
        var passes = List.of(pass("EASY", 60), pass("MEDIUM", 60));
        assertTrue(service.isTopicMastered(topic.getTopicId(), List.of(lesson, empty, suspended), passes));
        empty.setExercises(List.of(exercise("EASY", true)));
        assertFalse(service.isTopicMastered(topic.getTopicId(), List.of(lesson, empty), passes));
    }
    @Test void newLevelRequiresAnotherPassWithoutRemovingOldPasses() {
        var passes = List.of(pass("EASY", 60), pass("MEDIUM", 60));
        lesson.setExercises(List.of(exercise("EASY", true), exercise("MEDIUM", true), exercise("HARD", true)));
        assertFalse(service.isMastered(lesson, passes));
        assertEquals(List.of("EASY", "MEDIUM"), service.completedLevels(lesson, passes));
    }
}
