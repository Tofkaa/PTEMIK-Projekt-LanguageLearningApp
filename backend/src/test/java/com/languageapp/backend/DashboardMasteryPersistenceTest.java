package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.DifficultyLevel;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="DASHBOARD_TEST_DATABASE_URL", matches="jdbc:postgresql://[^/]+/languageapp_dashboard_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${DASHBOARD_TEST_DATABASE_URL}",
        "spring.jpa.hibernate.ddl-auto=create", "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class DashboardMasteryPersistenceTest {
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager manager;
    @Autowired MasteryService mastery;
    @Autowired LessonService lessons;
    @Autowired AchievementService achievements;
    @Autowired UserRepository users;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean SseService sse;
    @MockitoBean ImageStorageService images;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler scheduler;
    TransactionTemplate tx;
    Statistics statistics;
    record Fixture(User user,User other,Course course,LessonTopic topic) { }
    @BeforeEach void setup() {
        tx=new TransactionTemplate(manager);statistics=factory.unwrap(SessionFactory.class).getStatistics();
        tx.executeWithoutResult(status -> em.createQuery("update Achievement a set a.isActive=false").executeUpdate());
        reset(sse);
    }
    Course course() {
        Course c=new Course();c.setLanguageCode(UUID.randomUUID().toString().substring(0,8));c.setLanguageName("Dashboard");em.persist(c);return c;
    }
    User user(Course c) {
        String key=UUID.randomUUID().toString();User u=new User();u.setEmail(key+"@example.test");u.setName(key);
        u.setFriendCode(key.substring(0,8));u.setUserTag("1234");u.setVerified(true);u.setActiveCourse(c);u.setPreferredDifficulty(DifficultyLevel.MEDIUM);em.persist(u);return u;
    }
    LessonTopic topic(Course c,String name) {LessonTopic t=new LessonTopic();t.setCourse(c);t.setName(name);em.persist(t);return t;}
    Lesson lesson(LessonTopic t,String name) {
        Lesson l=new Lesson();l.setTopic(t);l.setTitle(name);l.setLanguage("en");l.setOrderIndex(1);l.setDescription("Card description");em.persist(l);return l;
    }
    Fixture fixture() {return tx.execute(status -> {Course c=course();return new Fixture(user(c),user(c),c,topic(c,"Alapok"));});}
    Exercise exercise(Lesson l,String level,boolean active) {
        Exercise e=new Exercise();e.setLesson(l);e.setDifficulty(level);e.setType("TRANSLATION");e.setActive(active);
        e.setContent(Map.of("question","QUESTION_PAYLOAD_NOT_FOR_DASHBOARD"+"x".repeat(512)));
        e.setCorrectAnswer(Map.of("answer","PRIVATE_EXPECTED_ANSWER"));em.persist(e);return e;
    }
    void pass(User u,Lesson l,String level,Boolean completed) {
        Progress p=new Progress();p.setUser(em.getReference(User.class,u.getUserId()));p.setLesson(l);p.setCompletedDifficulty(level);
        p.setIsCompleted(completed);p.setHighestScore(67);em.persist(p);
    }
    @Test void projectionsKeepActiveContentCourseOwnershipAndImperfectPassRules() {
        Fixture f=fixture();UUID[] ids=new UUID[3];
        tx.executeWithoutResult(status -> {
            LessonTopic t=em.getReference(LessonTopic.class,f.topic().getTopicId());
            Lesson first=lesson(t,"First");ids[0]=first.getLessonId();exercise(first,"easy",true);exercise(first,"MEDIUM",true);exercise(first,"HARD",false);
            pass(f.user(),first,"EASY",true);pass(f.user(),first,"medium",true);pass(f.user(),first,"HARD",false);
            Lesson second=lesson(t,"Second");ids[1]=second.getLessonId();exercise(second,"EASY",true);
            pass(f.other(),second,"EASY",true);pass(f.user(),second,"EASY",false);pass(f.user(),second,"MEDIUM",null);
            Lesson empty=lesson(t,"Empty");ids[2]=empty.getLessonId();exercise(empty,"HARD",false);pass(f.user(),empty,null,true);
            Lesson hidden=lesson(t,"Suspended lesson");hidden.setActive(false);exercise(hidden,"EASY",true);pass(f.user(),hidden,"EASY",true);
            LessonTopic hiddenTopic=topic(f.course(),"Suspended topic");hiddenTopic.setActive(false);
            Lesson hiddenInTopic=lesson(hiddenTopic,"Hidden");exercise(hiddenInTopic,"EASY",true);pass(f.user(),hiddenInTopic,"EASY",true);
            Course foreign=course();Lesson foreignLesson=lesson(topic(foreign,"Other course"),"Foreign");exercise(foreignLesson,"EASY",true);pass(f.user(),foreignLesson,"EASY",true);
        });
        var snapshot=mastery.getCourseSnapshot(f.user());assertEquals(3,snapshot.lessons().size());
        var first=snapshot.byLesson().get(ids[0]);assertEquals(List.of("EASY","MEDIUM"),first.availableLevels());
        assertEquals(first.availableLevels(),first.completedLevels());assertTrue(first.mastered());assertTrue(first.completed());
        assertFalse(snapshot.byLesson().get(ids[1]).completed());assertFalse(snapshot.byLesson().get(ids[1]).mastered());
        assertTrue(snapshot.byLesson().get(ids[2]).completed());assertFalse(snapshot.byLesson().get(ids[2]).mastered());
        assertTrue(snapshot.masteredTopics().isEmpty());assertEquals(1,mastery.countMastered(f.user(),false));assertEquals(0,mastery.countMastered(f.user(),true));
        var dashboard=lessons.getAllLessonsForUser(f.user().getEmail());assertEquals(3,dashboard.size());
        assertTrue(dashboard.stream().allMatch(l -> "MEDIUM".equals(l.getDifficulty())));
        assertTrue(dashboard.stream().noneMatch(l -> l.isTopicMastered()));
    }

    @Test void activeLevelAndLessonChangesAreVisibleWithoutAnyCacheReset() {
        Fixture f=fixture();UUID[] ids=new UUID[2];
        tx.executeWithoutResult(status -> {
            Lesson l=lesson(em.getReference(LessonTopic.class,f.topic().getTopicId()),"Changing");ids[0]=l.getLessonId();
            exercise(l,"EASY",true);ids[1]=exercise(l,"HARD",false).getExerciseId();pass(f.user(),l,"EASY",true);
            lesson(em.getReference(LessonTopic.class,f.topic().getTopicId()),"Empty is ignored for topic mastery");
        });
        assertEquals(1,mastery.countMastered(f.user(),true));
        tx.executeWithoutResult(status -> em.find(Exercise.class,ids[1]).setActive(true));
        assertEquals(0,mastery.countMastered(f.user(),false));assertEquals(0,mastery.countMastered(f.user(),true));
        tx.executeWithoutResult(status -> em.find(Exercise.class,ids[1]).setActive(false));
        assertEquals(1,mastery.countMastered(f.user(),true));
        tx.executeWithoutResult(status -> em.find(Lesson.class,ids[0]).setActive(false));
        assertEquals(0,mastery.countMastered(f.user(),true));assertEquals(1,lessons.getAllLessonsForUser(f.user().getEmail()).size());
        tx.executeWithoutResult(status -> em.find(LessonTopic.class,f.topic().getTopicId()).setActive(false));
        assertTrue(lessons.getAllLessonsForUser(f.user().getEmail()).isEmpty());
    }

    @Test void switchingCourseOrLearnerNeverReusesAnotherSnapshot() {
        Fixture f=fixture();UUID[] foreignId=new UUID[1];
        tx.executeWithoutResult(status -> {
            Lesson own=lesson(em.getReference(LessonTopic.class,f.topic().getTopicId()),"Own");exercise(own,"EASY",true);pass(f.user(),own,"EASY",true);
            Course other=course();foreignId[0]=other.getCourseId();Lesson foreign=lesson(topic(other,"Foreign"),"Foreign");exercise(foreign,"EASY",true);
        });
        assertTrue(lessons.getAllLessonsForUser(f.user().getEmail()).getFirst().isMastered());
        assertFalse(lessons.getAllLessonsForUser(f.other().getEmail()).getFirst().isMastered());
        tx.executeWithoutResult(status -> em.find(User.class,f.user().getUserId()).setActiveCourse(em.getReference(Course.class,foreignId[0])));
        var changed=lessons.getAllLessonsForUser(f.user().getEmail());assertEquals("Foreign",changed.getFirst().getTitle());assertFalse(changed.getFirst().isCompleted());
    }

    private void assertNoContentEntitiesLoaded() {
        for(Class<?> type:List.of(Lesson.class,LessonTopic.class,Exercise.class,Progress.class))
            assertEquals(0,statistics.getEntityStatistics(type.getName()).getLoadCount(),type.getSimpleName()+" must remain scalar");
    }
    long measureSnapshot(Fixture f,int count) {
        statistics.clear();var snapshot=mastery.getCourseSnapshot(f.user());
        assertEquals(count,snapshot.lessons().size());assertTrue(snapshot.byLesson().values().stream().allMatch(MasteryService.LessonMastery::mastered));
        assertEquals(Set.of(f.topic().getTopicId()),snapshot.masteredTopics());assertNoContentEntitiesLoaded();
        long queries=statistics.getPrepareStatementCount();
        statistics.clear();var counts=mastery.countMasteredBoth(f.user());
        assertEquals(count,counts.lessons());assertEquals(1,counts.topics());assertEquals(3,statistics.getPrepareStatementCount());
        return queries;
    }
    long measureDashboard(Fixture f,int count) throws Exception {
        statistics.clear();var response=lessons.getAllLessonsForUser(f.user().getEmail());assertEquals(count,response.size());
        assertTrue(response.stream().allMatch(l -> l.isMastered() && l.isTopicMastered()));assertNoContentEntitiesLoaded();
        long queries=statistics.getPrepareStatementCount();String serialized=json.writeValueAsString(response);
        assertFalse(serialized.contains("PRIVATE_EXPECTED_ANSWER"));assertFalse(serialized.contains("QUESTION_PAYLOAD_NOT_FOR_DASHBOARD"));
        return queries;
    }
    @Test void tenAndThousandCardsHaveBoundedQueriesAndNeverLoadExercisePayload() throws Exception {
        Fixture f=fixture();
        tx.executeWithoutResult(status -> {
            LessonTopic topic=em.getReference(LessonTopic.class,f.topic().getTopicId());
            for(int i=0;i<10;i++) {Lesson l=lesson(topic,"Card "+i);exercise(l,"EASY",true);pass(f.user(),l,"EASY",true);}
        });
        long smallSnapshot=measureSnapshot(f,10),smallDashboard=measureDashboard(f,10);
        tx.executeWithoutResult(status -> {
            LessonTopic topic=em.getReference(LessonTopic.class,f.topic().getTopicId());
            for(int i=10;i<1000;i++) {Lesson l=lesson(topic,"Card "+i);exercise(l,"EASY",true);pass(f.user(),l,"EASY",true);}
        });
        long largeSnapshot=measureSnapshot(f,1000),largeDashboard=measureDashboard(f,1000);
        assertEquals(3,smallSnapshot);assertEquals(smallSnapshot,largeSnapshot);
        assertEquals(smallDashboard,largeDashboard);assertTrue(largeDashboard<=8,"Dashboard query budget exceeded: "+largeDashboard);
        System.out.println("DASHBOARD_MASTERY_QUERY_COST cards=10 snapshot="+smallSnapshot+" dashboard="+smallDashboard+"; cards=1000 snapshot="+largeSnapshot+" dashboard="+largeDashboard);
    }

    @Test void historicalMasteryStillAwardsLessonAndTopicTrophiesOnce() {
        Fixture f=fixture();
        tx.executeWithoutResult(status -> {
            Lesson l=lesson(em.getReference(LessonTopic.class,f.topic().getTopicId()),"Passed");exercise(l,"EASY",true);pass(f.user(),l,"EASY",true);
            for(String type:List.of("LESSON_MASTERY","TOPIC_MASTERY")) {
                Achievement a=new Achievement();a.setName(type);a.setCriteria(Map.of("type",type,"target",1));em.persist(a);
            }
        });
        statistics.clear();
        var response=achievements.getUserAchievements(f.user().getUserId()).stream().filter(a -> a.isUnlocked()).toList();
        assertEquals(2,response.size());assertTrue(response.stream().allMatch(a -> a.isUnread()));assertNoContentEntitiesLoaded();
        verify(sse,times(2)).sendPing(f.user().getEmail());reset(sse);
        assertEquals(2,achievements.getUserAchievements(f.user().getUserId()).stream().filter(a -> a.isUnlocked()).count());verifyNoInteractions(sse);
    }
}
