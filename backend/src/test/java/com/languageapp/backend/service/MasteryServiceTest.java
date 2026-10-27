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

    private final UUID courseId = UUID.randomUUID();
    private User snapshotUser() {
        User user = new User(); user.setUserId(UUID.randomUUID());
        Course course = new Course(); course.setCourseId(courseId); user.setActiveCourse(course); return user;
    }
    private LessonRepository.CourseLesson overview(Lesson value) {
        return new LessonRepository.CourseLesson() {
            public UUID getLessonId() { return value.getLessonId(); }
            public UUID getTopicId() { return value.getTopic().getTopicId(); }
            public String getTopicName() { return value.getTopic().getName(); }
            public String getTitle() { return value.getTitle(); }
            public Integer getOrderIndex() { return value.getOrderIndex(); }
            public String getLanguage() { return value.getLanguage(); }
            public String getDescription() { return value.getDescription(); }
        };
    }
    private LessonRepository.AvailableLevel available(UUID id, String difficulty) {
        return new LessonRepository.AvailableLevel() {
            public UUID getLessonId() { return id; } public String getDifficulty() { return difficulty; }
        };
    }
    private ProgressRepository.PassedLevel completed(UUID id, String difficulty) {
        return new ProgressRepository.PassedLevel() {
            public UUID getLessonId() { return id; } public String getDifficulty() { return difficulty; }
        };
    }
    @Test void snapshotKeepsCanonicalOrderAndIgnoresRetiredLevelsWithoutBlockingThePath() {
        User user=snapshotUser();UUID id=lesson.getLessonId();
        when(lessons.findCourseOverview(courseId)).thenReturn(List.of(overview(lesson)));
        when(lessons.findAvailableCourseLevels(courseId)).thenReturn(List.of(available(id,"medium"),available(id,"EASY"),available(id,"easy"),available(id,"RETIRED")));
        when(progress.findPassedCourseLevels(user.getUserId(),courseId)).thenReturn(List.of(completed(id,"HARD"),completed(id,"Easy"),completed(id,"MEDIUM")));
        var snapshot=service.getCourseSnapshot(user);var state=snapshot.byLesson().get(id);
        assertEquals(List.of("EASY","MEDIUM"),state.availableLevels());assertEquals(state.availableLevels(),state.completedLevels());
        assertTrue(state.completed());assertEquals("HARD",state.completedDifficulty());assertTrue(state.mastered());
        assertTrue(snapshot.masteredTopics().contains(topic.getTopicId()));
        verify(lessons).findCourseOverview(courseId);verify(lessons).findAvailableCourseLevels(courseId);
        verify(progress).findPassedCourseLevels(user.getUserId(),courseId);verifyNoMoreInteractions(lessons,progress);
    }
    @Test void snapshotDoesNotGrantVacuousMasteryAndAcceptsLegacyNullDifficulty() {
        User user=snapshotUser();UUID id=lesson.getLessonId();
        when(lessons.findCourseOverview(courseId)).thenReturn(List.of(overview(lesson)));
        when(progress.findPassedCourseLevels(user.getUserId(),courseId)).thenReturn(List.of(completed(id,null)));
        var state=service.getCourseSnapshot(user).byLesson().get(id);
        assertTrue(state.completed());assertNull(state.completedDifficulty());assertFalse(state.mastered());
        assertTrue(state.availableLevels().isEmpty());assertTrue(state.completedLevels().isEmpty());
        assertEquals(0,service.countMastered(user,true));
    }
    @Test void snapshotIsFreshAcrossCallsAndCollectionsCannotBeMutated() {
        User user=snapshotUser();UUID id=lesson.getLessonId();
        when(lessons.findCourseOverview(courseId)).thenReturn(List.of(overview(lesson)));
        when(lessons.findAvailableCourseLevels(courseId)).thenReturn(List.of(available(id,"EASY")),List.of(available(id,"EASY"),available(id,"HARD")));
        when(progress.findPassedCourseLevels(user.getUserId(),courseId)).thenReturn(List.of(completed(id,"EASY")));
        var snapshot=service.getCourseSnapshot(user);
        assertTrue(snapshot.byLesson().get(id).mastered());assertFalse(service.getCourseSnapshot(user).byLesson().get(id).mastered());
        assertThrows(UnsupportedOperationException.class,()->snapshot.lessons().clear());
        assertThrows(UnsupportedOperationException.class,()->snapshot.byLesson().clear());
        assertThrows(UnsupportedOperationException.class,()->snapshot.masteredTopics().clear());
        assertThrows(UnsupportedOperationException.class,()->snapshot.byLesson().get(id).completedLevels().clear());
    }
    @Test void snapshotMatchesExistingRulesAcrossMixedLessonsAndSuccessfulLevels() {
        User user=snapshotUser();var all=new ArrayList<Lesson>();var completions=new ArrayList<Progress>();
        var levels=new ArrayList<LessonRepository.AvailableLevel>();var passes=new ArrayList<ProgressRepository.PassedLevel>();
        for(int i=0;i<30;i++) {
            Lesson item=new Lesson();item.setLessonId(UUID.randomUUID());item.setTopic(topic);
            item.setExercises(i%5==0?List.of():List.of(exercise("EASY",true),exercise("medium",true),exercise("HARD",i%3==0)));
            all.add(item);
            for(var exercise:item.getExercises())if(exercise.isActive())levels.add(available(item.getLessonId(),exercise.getDifficulty()));
            for(String level:List.of("EASY","MEDIUM","HARD")) {
                Progress p=new Progress();p.setLesson(item);p.setCompletedDifficulty(level);p.setHighestScore(67);p.setIsCompleted(i%2==0||level.equals("EASY"));
                completions.add(p);if(Boolean.TRUE.equals(p.getIsCompleted()))passes.add(completed(item.getLessonId(),level));
            }
        }
        when(lessons.findCourseOverview(courseId)).thenReturn(all.stream().map(this::overview).toList());
        when(lessons.findAvailableCourseLevels(courseId)).thenReturn(levels);
        when(progress.findPassedCourseLevels(user.getUserId(),courseId)).thenReturn(passes);
        var snapshot=service.getCourseSnapshot(user);
        for(var item:all) {
            var state=snapshot.byLesson().get(item.getLessonId());
            assertEquals(service.availableLevels(item),state.availableLevels());
            assertEquals(service.completedLevels(item,completions),state.completedLevels());
            assertEquals(service.isMastered(item,completions),state.mastered());
        }
        assertEquals(service.isTopicMastered(topic.getTopicId(),all,completions),snapshot.masteredTopics().contains(topic.getTopicId()));
    }
}
