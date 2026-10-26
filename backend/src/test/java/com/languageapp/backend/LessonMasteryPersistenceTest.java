package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.dto.request.*;
import com.languageapp.backend.dto.response.*;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.DifficultyLevel;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.scheduler.AssignmentExpiryScheduler;
import com.languageapp.backend.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: table creation is permitted only in an explicitly disposable PostgreSQL database. */
@EnabledIfEnvironmentVariable(named="LESSON_MASTERY_TEST_DATABASE_URL", matches="jdbc:postgresql://[^/]+/languageapp_mastery_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${LESSON_MASTERY_TEST_DATABASE_URL}",
        "spring.jpa.hibernate.ddl-auto=create", "spring.jpa.show-sql=false"})
class LessonMasteryPersistenceTest {
    @Autowired LessonService lessons;
    @Autowired EvaluationService evaluation;
    @Autowired UserDifficultyCalculator calculator;
    @Autowired AchievementService achievements;
    @Autowired UserRepository users;
    @Autowired FriendshipService friendships;
    @Autowired ResultRepository results;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean SseService sse;
    @MockitoBean StreakService streak;
    @MockitoBean AssignmentExpiryScheduler expiry;
    private TransactionTemplate tx;
    record Fixture(UUID userId, String email, UUID lessonId, UUID courseId) {}

    @BeforeEach void setup() { tx = new TransactionTemplate(manager); }
    Fixture fixture() {
        return tx.execute(status -> {
            String key = UUID.randomUUID().toString().substring(0,8);
            Course course = new Course(); course.setLanguageCode(key); course.setLanguageName("Test"); em.persist(course);
            User user = new User(); user.setVerified(true); user.setName("Mastery test"); user.setEmail(key+"@example.test");
            user.setActiveCourse(course); user.setPreferredDifficulty(DifficultyLevel.EASY); user.setXp(0); em.persist(user);
            LessonTopic topic = new LessonTopic(); topic.setName("Mastery"); topic.setCourse(course); em.persist(topic);
            Lesson lesson = new Lesson(); lesson.setTitle("Fruit"); lesson.setTopic(topic); em.persist(lesson);
            for (int i=0; i<4; i++) {
                Exercise e = new Exercise(); e.setLesson(lesson); e.setType("TRANSLATION");
                e.setDifficulty(i==3 ? "MEDIUM" : "EASY"); e.setContent(Map.of("question", "Alma"));
                e.setCorrectAnswer(Map.of("answer", "apple")); em.persist(e);
            }
            Progress p = new Progress(); p.setUser(user); p.setLesson(lesson); p.setCompletedDifficulty("MEDIUM");
            p.setIsCompleted(true); p.setHighestScore(67); em.persist(p);
            Achievement a = new Achievement(); a.setName("Mastery "+key); a.setDescription("Historical and new passes");
            a.setCriteria(Map.of("type", "LESSON_MASTERY", "target", 1)); a.setIconUrl("🏅"); em.persist(a);
            em.flush(); return new Fixture(user.getUserId(), user.getEmail(), lesson.getLessonId(), course.getCourseId());
        });
    }
    LessonSubmitRequest answers(LessonStartResponse start, boolean imperfect) {
        LessonSubmitRequest r = new LessonSubmitRequest(); r.setAttemptId(start.attemptId());
        r.setAnswers(new ArrayList<>(start.exercises().stream().map(e -> {
            ExerciseSubmission s = new ExerciseSubmission(); s.setExerciseId(e.getExerciseId()); s.setAnswer("apple"); return s;
        }).toList()));
        if (imperfect) r.getAnswers().getFirst().setAnswer("wrong"); return r;
    }
    @Test void allocationAndAnswersSurviveSeparateTransactionsAndProfileContentChanges() {
        Fixture f=fixture(); var start=lessons.startAttempt(f.lessonId(), f.email(), null, null);
        tx.executeWithoutResult(status -> {
            users.findById(f.userId()).orElseThrow().setPreferredDifficulty(DifficultyLevel.HARD);
            em.find(Exercise.class, start.exercises().getFirst().getExerciseId()).setCorrectAnswer(Map.of("answer", "changed"));
        });
        assertTrue(evaluation.checkAttemptExercise(f.lessonId(),start.attemptId(),start.exercises().getFirst().getExerciseId(),f.email(),"apple").isCorrect());
        var response=evaluation.evaluateLesson(f.userId(), f.lessonId(), null, answers(start,false));
        assertEquals(100,response.getScore()); assertEquals(3,response.getTotalQuestionsCount());
        assertTrue(response.isLessonMastered()); assertTrue(response.isTopicMastered());
        // JSON response round-trip also makes retrying a completed POST idempotent.
        var repeated=evaluation.evaluateLesson(f.userId(), f.lessonId(), null, answers(start,false));
        assertEquals(response.getResultId(),repeated.getResultId()); assertEquals(30,repeated.getXpEarned());
        assertEquals(30,users.findById(f.userId()).orElseThrow().getXp());
    }
    @Test void imperfectManualReplayCountsForMasteryButNeverForAdaptiveHistory() {
        Fixture f=fixture(); var start=lessons.startAttempt(f.lessonId(),f.email(),null,"EASY");
        var response=evaluation.evaluateLesson(f.userId(),f.lessonId(),null,answers(start,true));
        assertEquals(67,response.getScore()); assertTrue(response.isPassed()); assertTrue(response.isLessonMastered());
        assertTrue(results.findByUserUserId(f.userId()).getFirst().isManualPractice());
        tx.executeWithoutResult(status -> users.findById(f.userId()).orElseThrow().setPreferredDifficulty(DifficultyLevel.DYNAMIC));
        assertEquals("EASY",tx.execute(status -> calculator.determineTargetDifficulty(users.findById(f.userId()).orElseThrow())));
        assertEquals(0,results.countByUserUserIdAndLesson_Topic_Course_CourseIdAndIsChallengeResultFalseAndManualPracticeFalseAndIsTestResultFalse(f.userId(),f.courseId()));
        assertTrue(achievements.getUserAchievements(f.userId()).stream().anyMatch(AchievementResponse::isUnlocked));
    }
    @Test void parallelSubmissionsOfSameAttemptHaveOneResultAndOneXpAward() throws Exception {
        Fixture f=fixture(); var start=lessons.startAttempt(f.lessonId(),f.email(),null,"EASY");
        var response=parallel(() -> evaluation.evaluateLesson(f.userId(),f.lessonId(),null,answers(start,false)));
        assertEquals(response.getFirst().getResultId(),response.getLast().getResultId());
        assertEquals(1,results.findByUserUserId(f.userId()).size()); assertEquals(30,users.findById(f.userId()).orElseThrow().getXp());
    }
    @Test void parallelDifferentAttemptsAtSameLevelCannotFarmXp() throws Exception {
        Fixture f=fixture(); var a=lessons.startAttempt(f.lessonId(),f.email(),null,"EASY");
        var b=lessons.startAttempt(f.lessonId(),f.email(),null,"EASY");
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var x=pool.submit(() -> {gate.await();return evaluation.evaluateLesson(f.userId(),f.lessonId(),null,answers(a,false));});
            var y=pool.submit(() -> {gate.await();return evaluation.evaluateLesson(f.userId(),f.lessonId(),null,answers(b,false));});
            gate.countDown(); assertEquals(30,x.get(20,TimeUnit.SECONDS).getXpEarned()+y.get(20,TimeUnit.SECONDS).getXpEarned());
        } finally {pool.shutdownNow();}
        assertEquals(2,results.findByUserUserId(f.userId()).size());assertEquals(30,users.findById(f.userId()).orElseThrow().getXp());
    }
    private <T> List<T> parallel(Callable<T> action) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        Callable<T> task=()->{gate.await();return action.call();};
        try {var a=pool.submit(task);var b=pool.submit(task);gate.countDown();return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));}
        finally {pool.shutdownNow();}
    }
    @Test void pendingAwardsSurviveReloadAndPresentationIsDistinctFromRead() {
        Fixture f = fixture();
        var start = lessons.startAttempt(f.lessonId(), f.email(), null, "EASY");
        var result = evaluation.evaluateLesson(f.userId(), f.lessonId(), null, answers(start, false));
        assertFalse(result.getNewAchievements().isEmpty());
        var awardId = result.getNewAchievements().getFirst().userAchievementId();
        assertTrue(achievements.getPendingFeedback(f.userId()).stream().anyMatch(a -> a.userAchievementId().equals(awardId)));
        achievements.acknowledgeFeedback(f.userId(), List.of(awardId), false);
        assertTrue(achievements.getPendingFeedback(f.userId()).stream().noneMatch(a -> a.userAchievementId().equals(awardId)));
        assertTrue(achievements.getUserAchievements(f.userId()).stream().anyMatch(a -> awardId.equals(a.getUserAchievementId()) && a.isUnread()));
        achievements.acknowledgeFeedback(f.userId(), List.of(awardId), true);
        assertFalse(achievements.getUserAchievements(f.userId()).stream().anyMatch(a -> awardId.equals(a.getUserAchievementId()) && a.isUnread()));
    }

    @Test void rolledBackAwardsAreNeitherPersistedNorAnnounced() {
        Fixture f = fixture();
        tx.execute(status -> {
            Achievement trophy = new Achievement(); trophy.setName("Rollback "+UUID.randomUUID());
            trophy.setDescription("Only committed awards notify"); trophy.setIconUrl("star");
            trophy.setCriteria(Map.of("type", "PERFECT_SCORE")); em.persist(trophy); return trophy;
        });
        org.mockito.Mockito.clearInvocations(sse);
        tx.executeWithoutResult(status -> {
            achievements.checkAndAwardAchievements(users.findById(f.userId()).orElseThrow(), 100);
            org.mockito.Mockito.verifyNoInteractions(sse);
            status.setRollbackOnly();
        });
        assertTrue(achievements.getPendingFeedback(f.userId()).isEmpty());
        org.mockito.Mockito.verifyNoInteractions(sse);
    }

    @Test void friendAcceptanceAwardsBothParticipantsAfterCommit() {
        Fixture a = fixture(), b = fixture();
        UUID friendshipId = tx.execute(status -> {
            Achievement trophy = new Achievement(); trophy.setName("Social "+UUID.randomUUID());
            trophy.setDescription("Friendship trigger"); trophy.setIconUrl("star");
            trophy.setCriteria(Map.of("type", "SOCIAL_FRIENDS", "target", 1)); em.persist(trophy);
            Friendship friendship = new Friendship(); friendship.setUser(users.findById(a.userId()).orElseThrow());
            friendship.setFriend(users.findById(b.userId()).orElseThrow());
            friendship.setStatus(com.languageapp.backend.enums.FriendshipStatus.PENDING); em.persist(friendship);
            em.flush(); return friendship.getFriendshipId();
        });
        assertTrue(achievements.getPendingFeedback(a.userId()).isEmpty());
        assertTrue(achievements.getPendingFeedback(b.userId()).isEmpty());
        friendships.acceptRequest(friendshipId, b.userId());
        assertFalse(achievements.getPendingFeedback(a.userId()).isEmpty());
        assertFalse(achievements.getPendingFeedback(b.userId()).isEmpty());
    }

}
