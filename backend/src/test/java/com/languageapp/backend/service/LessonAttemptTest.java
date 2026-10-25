package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.*;
import com.languageapp.backend.dto.response.LessonSubmitResponse;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.*;
import com.languageapp.backend.exception.*;
import com.languageapp.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LessonAttemptTest {
    @Mock UserRepository users;
    @Mock LessonRepository lessons;
    @Mock ResultRepository results;
    @Mock ProgressRepository progress;
    @Mock UserDifficultyCalculator calculator;
    @Mock ExerciseRepository exercises;
    @Mock AchievementRepository achievements;
    @Mock AchievementService achievementService;
    @Mock ChallengeRepository challenges;
    @Mock ChallengeService challengeService;
    @Mock AssignmentSessionRepository sessions;
    @Mock StreakService streak;
    @Mock CourseRepository courses;
    @Mock LessonAttemptRepository attempts;
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC);
    @InjectMocks EvaluationService evaluation;
    @InjectMocks LessonService service;
    private User user;
    private Lesson lesson;
    private LessonAttempt attempt;
    private List<Progress> passes;

    @BeforeEach void setup() {
        Course course = new Course(); course.setCourseId(UUID.randomUUID()); course.setLanguageCode("en");
        LessonTopic topic = new LessonTopic(); topic.setTopicId(UUID.randomUUID()); topic.setCourse(course);
        lesson = new Lesson(); lesson.setLessonId(UUID.randomUUID()); lesson.setTitle("Fruit"); lesson.setTopic(topic);
        List<Exercise> items = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Exercise e = new Exercise(); e.setExerciseId(UUID.randomUUID()); e.setLesson(lesson);
            e.setDifficulty(i == 3 ? "MEDIUM" : "EASY"); e.setType("TRANSLATION");
            e.setContent(Map.of("question", "Alma")); e.setCorrectAnswer(Map.of("answer", "apple")); items.add(e);
        }
        lesson.setExercises(items);
        user = new User(); user.setUserId(UUID.randomUUID()); user.setEmail("learner@test.local");
        user.setRole(Role.STUDENT); user.setActiveCourse(course); user.setPreferredDifficulty(DifficultyLevel.HARD); user.setXp(0);
        attempt = new LessonAttempt(); attempt.setAttemptId(UUID.randomUUID()); attempt.setUser(user); attempt.setLesson(lesson);
        attempt.setDifficulty("EASY"); attempt.setExerciseIds(items.subList(0,3).stream().map(Exercise::getExerciseId).toList());
        attempt.setExerciseSnapshot(items.subList(0,3).stream().map(LessonAttempt.AllocatedExercise::capture).toList());
        passes = new ArrayList<>();
        var mastery = new MasteryService(lessons, progress);
        ReflectionTestUtils.setField(service, "masteryService", mastery);
        ReflectionTestUtils.setField(service, "exerciseSalt", "test");
        ReflectionTestUtils.setField(evaluation, "masteryService", mastery);
        lenient().when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        lenient().when(users.findForLearningUpdate(user.getUserId())).thenReturn(Optional.of(user));
        lenient().when(lessons.findById(lesson.getLessonId())).thenReturn(Optional.of(lesson));
        lenient().when(lessons.findByTopic_Course_CourseId(any())).thenReturn(List.of(lesson));
        lenient().when(attempts.findForSubmission(attempt.getAttemptId())).thenReturn(Optional.of(attempt));
        lenient().when(attempts.save(any())).thenAnswer(inv -> { LessonAttempt a = inv.getArgument(0); if(a.getAttemptId()==null) a.setAttemptId(UUID.randomUUID()); return a; });
        lenient().when(progress.findByUserUserId(user.getUserId())).thenAnswer(inv -> passes);
        lenient().when(progress.findByUserUserIdAndLessonLessonIdAndCompletedDifficulty(any(), any(), anyString()))
                .thenAnswer(inv -> passes.stream().filter(p -> inv.getArgument(2).equals(p.getCompletedDifficulty())).findFirst());
        lenient().when(progress.save(any())).thenAnswer(inv -> { Progress p = inv.getArgument(0); if (!passes.contains(p)) passes.add(p); return p; });
        lenient().when(results.save(any())).thenAnswer(inv -> { Result r = inv.getArgument(0); r.setResultId(UUID.randomUUID()); return r; });
    }
    private LessonSubmitRequest answers() {
        LessonSubmitRequest r = new LessonSubmitRequest(); r.setAttemptId(attempt.getAttemptId());
        r.setAnswers(new ArrayList<>(attempt.getExerciseIds().stream().map(id -> {
            ExerciseSubmission s = new ExerciseSubmission(); s.setExerciseId(id); s.setAnswer("apple"); return s;
        }).toList())); return r;
    }
    private Progress pass(String level) {
        Progress p = new Progress(); p.setUser(user); p.setLesson(lesson); p.setCompletedDifficulty(level);
        p.setHighestScore(67); p.setIsCompleted(true); passes.add(p); return p;
    }
    @Test void changedProfileAndNewExercisesDoNotChangeAllocatedEvaluation() {
        attempt.setManualPractice(true);
        lesson.getExercises().getFirst().setCorrectAnswer(Map.of("answer", "changed after start"));
        var response = evaluation.evaluateLesson(user.getUserId(), lesson.getLessonId(), null, answers());
        assertEquals(3, response.getTotalQuestionsCount()); assertEquals(100, response.getScore());
        assertEquals("EASY", passes.getFirst().getCompletedDifficulty());
        verifyNoInteractions(calculator);
    }
    @Test void successfulImperfectReplayUnlocksMasteryAndXpOnlyOnce() {
        attempt.setManualPractice(true); pass("MEDIUM");
        var request = answers(); request.getAnswers().getFirst().setAnswer("wrong");
        var response = evaluation.evaluateLesson(user.getUserId(), lesson.getLessonId(), null, request);
        assertTrue(response.isPassed()); assertEquals(67, response.getScore());
        assertTrue(response.isLessonMastered()); assertTrue(response.isTopicMastered());
        assertEquals(20, response.getXpEarned()); assertEquals(20, user.getXp());
        assertSame(response, evaluation.evaluateLesson(user.getUserId(), lesson.getLessonId(), null, request));
        verify(results, times(1)).save(any()); verify(streak, times(1)).updateActivity(user);
        var captured = ArgumentCaptor.forClass(Result.class); verify(results).save(captured.capture());
        assertTrue(captured.getValue().isManualPractice());
    }
    @Test void anotherAttemptAtAnAlreadyPassedLevelDoesNotAwardXp() {
        attempt.setManualPractice(true); pass("EASY");
        assertEquals(0, evaluation.evaluateLesson(user.getUserId(), lesson.getLessonId(), null, answers()).getXpEarned());
        assertEquals(0, user.getXp());
    }
    @Test void foreignAttemptCannotBeSubmittedOrReadAsCompletedResult() {
        User foreign = new User(); foreign.setUserId(UUID.randomUUID()); attempt.setUser(foreign);
        attempt.setSubmissionResult(LessonSubmitResponse.builder().passed(true).build());
        assertThrows(ForbiddenException.class, () -> evaluation.evaluateLesson(user.getUserId(), lesson.getLessonId(), null, answers()));
    }
    @Test void answersOutsideAllocationAndDuplicatesAreRejected() {
        var request = answers(); request.getAnswers().getFirst().setExerciseId(lesson.getExercises().getLast().getExerciseId());
        assertThrows(ForbiddenException.class, () -> evaluation.evaluateLesson(user.getUserId(), lesson.getLessonId(), null, request));
        var duplicate = answers(); duplicate.getAnswers().add(duplicate.getAnswers().getFirst());
        assertThrows(ForbiddenException.class, () -> evaluation.evaluateLesson(user.getUserId(), lesson.getLessonId(), null, duplicate));
        verify(results, never()).save(any());
    }
    @Test void normalStartUsesStrictFixedLevel() {
        when(calculator.determineTargetDifficulty(user)).thenReturn("MEDIUM");
        var start = service.startAttempt(lesson.getLessonId(), user.getEmail(), null, null);
        assertEquals("MEDIUM", start.difficulty()); assertEquals(1, start.exercises().size());
        assertFalse(start.manualPractice()); assertNotNull(start.attemptId());
    }
    @Test void replayRequiresInitialPassAndAvailableLevel() {
        assertThrows(ForbiddenException.class, () -> service.startAttempt(lesson.getLessonId(), user.getEmail(), null, "MEDIUM"));
        pass("EASY");
        var start = service.startAttempt(lesson.getLessonId(), user.getEmail(), null, "MEDIUM");
        assertTrue(start.manualPractice()); assertEquals("MEDIUM", start.difficulty());
        assertThrows(ConflictException.class, () -> service.startAttempt(lesson.getLessonId(), user.getEmail(), null, "HARD"));
        verifyNoInteractions(calculator);
    }
    @Test void challengeCannotOverrideDifficultyUsingReplayParameter() {
        assertThrows(ForbiddenException.class, () -> service.startAttempt(lesson.getLessonId(), user.getEmail(), UUID.randomUUID(), "EASY"));
        verify(attempts, never()).save(any());
    }
}
