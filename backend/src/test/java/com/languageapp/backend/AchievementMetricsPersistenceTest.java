package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.*;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import static org.mockito.Mockito.*;

/** Opt-in query-cost and rule-equivalence tests; never creates a schema in the application DB. */
@EnabledIfEnvironmentVariable(named="ACHIEVEMENT_TEST_DATABASE_URL", matches="jdbc:postgresql://[^/]+/languageapp_achievement_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${ACHIEVEMENT_TEST_DATABASE_URL}",
        "spring.jpa.hibernate.ddl-auto=create", "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class AchievementMetricsPersistenceTest {
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager manager;
    @Autowired AchievementService service;
    @Autowired ProgressRepository progress;
    @Autowired FriendshipRepository friends;
    @Autowired ChallengeRepository challenges;
    @Autowired UserAchievementRepository awards;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean SseService sse;
    @MockitoBean ImageStorageService images;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler scheduler;
    @MockitoBean MasteryService mastery;
    TransactionTemplate tx;
    Statistics statistics;
    record Fixture(User user, User other, User third, Course course, Lesson lesson, Lesson second) { }
    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        statistics = factory.unwrap(SessionFactory.class).getStatistics();
        tx.executeWithoutResult(status -> em.createQuery("update Achievement a set a.isActive=false").executeUpdate());
        reset(sse, mastery);
    }
    User user(Course course) {
        User u = new User(); String key = UUID.randomUUID().toString();
        u.setEmail(key+"@example.test"); u.setName(key); u.setUserTag("1234"); u.setFriendCode(key.substring(0,8));
        u.setVerified(true); u.setActiveCourse(course); em.persist(u); return u;
    }
    Fixture fixture() {
        return tx.execute(status -> {
            Course c = new Course(); c.setLanguageCode(UUID.randomUUID().toString().substring(0,8)); c.setLanguageName("Metrics"); em.persist(c);
            LessonTopic t = new LessonTopic(); t.setCourse(c); t.setName("Metrics"); em.persist(t);
            Lesson one = new Lesson(); one.setTopic(t); one.setTitle("One"); em.persist(one);
            Lesson two = new Lesson(); two.setTopic(t); two.setTitle("Two"); em.persist(two);
            return new Fixture(user(c),user(c),user(c),c,one,two);
        });
    }
    void completion(User user, Lesson lesson, Boolean completed, String level) {
        Progress p = new Progress(); p.setUser(em.getReference(User.class,user.getUserId())); p.setLesson(em.getReference(Lesson.class,lesson.getLessonId()));
        p.setIsCompleted(completed); p.setCompletedDifficulty(level); em.persist(p);
    }
    void friendship(User sender, User receiver, FriendshipStatus status) {
        Friendship f = new Friendship(); f.setUser(em.getReference(User.class,sender.getUserId()));
        f.setFriend(em.getReference(User.class,receiver.getUserId())); f.setStatus(status); em.persist(f);
    }
    void challenge(User sender, User receiver, User winner, Lesson lesson, ChallengeStatus status) {
        Challenge c = new Challenge(); c.setChallenger(em.getReference(User.class,sender.getUserId()));
        c.setOpponent(em.getReference(User.class,receiver.getUserId())); c.setLesson(em.getReference(Lesson.class,lesson.getLessonId()));
        if(winner!=null)c.setWinner(em.getReference(User.class,winner.getUserId())); c.setStatus(status); em.persist(c);
    }
    Achievement trophy(String type, int target) {
        Achievement a = new Achievement(); a.setName(type+UUID.randomUUID()); a.setCriteria(Map.of("type",type,"target",target)); em.persist(a); return a;
    }
    void award(User user, Achievement trophy) {
        UserAchievement a = new UserAchievement(); a.setUser(em.getReference(User.class,user.getUserId())); a.setAchievement(trophy);
        a.setFeedbackEnabled(true); em.persist(a);
    }

    @Test void completedCountKeepsDistinctLessonsAndIgnoresOtherUsersAndIncompleteLevels() {
        Fixture f=fixture();
        tx.executeWithoutResult(status -> {
            for(String level:List.of("EASY","MEDIUM","HARD"))completion(f.user(),f.lesson(),true,level);
            completion(f.user(),f.second(),false,"EASY"); completion(f.user(),f.second(),null,"MEDIUM");
            completion(f.other(),f.second(),true,"HARD");
        });
        assertEquals(1,progress.countCompletedLessons(f.user().getUserId()));
        assertEquals(1,progress.countCompletedLessons(f.other().getUserId()));
        assertEquals(0,progress.countCompletedLessons(f.third().getUserId()));
    }

    @Test void socialCountsKeepDirectionStatusParticipantAndWinnerRules() {
        Fixture f=fixture();
        tx.executeWithoutResult(status -> {
            friendship(f.user(),f.other(),FriendshipStatus.ACCEPTED);
            friendship(f.third(),f.user(),FriendshipStatus.ACCEPTED);
            friendship(f.user(),f.third(),FriendshipStatus.PENDING);
            friendship(f.other(),f.third(),FriendshipStatus.ACCEPTED);
            challenge(f.user(),f.other(),f.user(),f.lesson(),ChallengeStatus.COMPLETED);
            challenge(f.other(),f.user(),f.user(),f.lesson(),ChallengeStatus.COMPLETED);
            challenge(f.user(),f.other(),f.other(),f.lesson(),ChallengeStatus.COMPLETED);
            challenge(f.user(),f.other(),null,f.lesson(),ChallengeStatus.COMPLETED);
            challenge(f.user(),f.other(),f.user(),f.lesson(),ChallengeStatus.EXPIRED);
            challenge(f.other(),f.third(),f.user(),f.lesson(),ChallengeStatus.COMPLETED);
        });
        assertEquals(2,friends.countFriendships(f.user().getUserId(),FriendshipStatus.ACCEPTED));
        assertEquals(2,challenges.countWins(f.user().getUserId(),ChallengeStatus.COMPLETED));
        assertEquals(1,challenges.countWins(f.other().getUserId(),ChallengeStatus.COMPLETED));
    }

    long measureEvaluation(Fixture f) {
        return tx.execute(status -> {
            statistics.clear();
            assertTrue(service.checkAndAwardAchievements(f.user(),-1).isEmpty()); em.flush();
            for(Class<?> history:List.of(Progress.class,Challenge.class,Friendship.class,StudentVocabulary.class,UserAchievement.class)) {
                assertEquals(0,statistics.getEntityStatistics(history.getName()).getLoadCount(),history.getSimpleName()+" must not be materialized");
            }
            return statistics.getPrepareStatementCount();
        });
    }
    @Test void tenAndThousandThresholdsUseTheSameNumberOfQueriesWithoutLoadingHistory() {
        Fixture f=fixture();
        String[] types={"LESSON_COUNT","SOCIAL_FRIENDS","SOCIAL_DUELS","VOCAB_COUNT","MASTERED_VOCAB_COUNT"};
        tx.executeWithoutResult(status -> {
            for(int i=0;i<10;i++)trophy(types[i%types.length],Integer.MAX_VALUE);
            completion(f.user(),f.lesson(),true,"EASY");
            friendship(f.user(),f.other(),FriendshipStatus.ACCEPTED);
            challenge(f.user(),f.other(),f.user(),f.lesson(),ChallengeStatus.COMPLETED);
            StudentVocabulary v=new StudentVocabulary();v.setUser(em.getReference(User.class,f.user().getUserId()));
            v.setCourse(em.getReference(Course.class,f.course().getCourseId()));v.setWord("word");v.setTranslation("meaning");v.setSrsLevel(6);em.persist(v);
        });
        long small=measureEvaluation(f);
        tx.executeWithoutResult(status -> {
            for(int i=10;i<1000;i++)trophy(types[i%types.length],Integer.MAX_VALUE);
            LessonTopic topic=em.getReference(Lesson.class,f.lesson().getLessonId()).getTopic();
            for(int i=0;i<1000;i++) {
                Lesson lesson=new Lesson();lesson.setTopic(topic);lesson.setTitle("Historical "+i);em.persist(lesson);
                completion(i%2==0?f.user():f.other(),lesson,true,"EASY");
                challenge(f.user(),f.other(),i%2==0?f.user():f.other(),f.lesson(),ChallengeStatus.COMPLETED);
            }
        });
        long large=measureEvaluation(f);
        assertEquals(small,large);assertTrue(large<=12,"Query count must be bounded, got "+large);
        System.out.println("ACHIEVEMENT_QUERY_COST thresholds=10 queries="+small+"; thresholds=1000 extraProgress=1000 extraChallenges=1000 queries="+large);
    }

    @Test void pendingFeedbackFetchesNamesInOneQueryAndKeepsOwnership() {
        Fixture f=fixture();
        tx.executeWithoutResult(status -> {
            for(int i=0;i<100;i++)award(f.user(),trophy("PERFECT_SCORE",1));
            award(f.other(),trophy("PERFECT_SCORE",1));
        });
        statistics.clear();
        var pending=service.getPendingFeedback(f.user().getUserId());
        assertEquals(100,pending.size());assertTrue(pending.stream().allMatch(a -> a.name().startsWith("PERFECT_SCORE")));
        assertEquals(1,statistics.getPrepareStatementCount());
        assertEquals(0,statistics.getEntityStatistics(User.class.getName()).getLoadCount());
    }

    @Test void concurrentEvaluationStillAwardsOnceAndOnlyOneCallerReceivesFeedback() throws Exception {
        Fixture f=fixture();tx.executeWithoutResult(status -> trophy("PERFECT_SCORE",1));
        ExecutorService workers=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<Integer>> results=new ArrayList<>();
            for(int i=0;i<2;i++)results.add(workers.submit(() -> {assertTrue(start.await(5,TimeUnit.SECONDS));return service.checkAndAwardAchievements(f.user(),100).size();}));
            start.countDown();assertEquals(1,results.get(0).get(15,TimeUnit.SECONDS)+results.get(1).get(15,TimeUnit.SECONDS));
            assertEquals(1,awards.findOwnedAchievementIds(f.user().getUserId()).size());
            verify(sse).sendPing(f.user().getEmail());
        } finally { workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS)); }
    }

    @Test void profileIncludesReconciledMasteryAndPreservesUnreadFlags() {
        Fixture f=fixture();
        tx.executeWithoutResult(status -> {
            award(f.user(),trophy("PERFECT_SCORE",1));trophy("LESSON_MASTERY",1);trophy("LESSON_MASTERY",2);
        });
        when(mastery.countMasteredBoth(any(User.class))).thenReturn(new MasteryService.MasteryCounts(2,0));
        var result=service.getUserAchievements(f.user().getUserId()).stream().filter(a -> a.isUnlocked()).toList();
        assertEquals(3,result.size());assertTrue(result.stream().allMatch(a -> a.isUnread()));
        assertTrue(result.stream().allMatch(a -> a.getUserAchievementId()!=null && a.getAchievedAt()!=null));
        verify(mastery).countMasteredBoth(any(User.class));
    }

    @Test void rollbackDoesNotSaveOrNotifyTheAward() {
        Fixture f=fixture();tx.executeWithoutResult(status -> trophy("PERFECT_SCORE",1));
        assertThrows(IllegalStateException.class,() -> tx.executeWithoutResult(status -> {
            assertEquals(1,service.checkAndAwardAchievements(f.user(),100).size());throw new IllegalStateException("rollback fixture");
        }));
        assertTrue(awards.findOwnedAchievementIds(f.user().getUserId()).isEmpty());verifyNoInteractions(sse);
    }
}
