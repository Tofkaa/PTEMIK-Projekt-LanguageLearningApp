package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.security.*;
import com.languageapp.backend.service.*;
import com.languageapp.backend.exception.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.springframework.web.context.WebApplicationContext;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="COURSE_SCOPE_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_course_scope_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${COURSE_SCOPE_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.show-sql=false","spring.datasource.hikari.maximum-pool-size=2","spring.datasource.hikari.connection-timeout=1000",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class CourseScopePersistenceTest {
    @Autowired VocabularyService service;
    @Autowired UserService profiles;
    @Autowired LessonService lessons;
    @Autowired VocabularyLookupStore store;
    @Autowired UserRepository users;
    @Autowired StudentVocabularyRepository words;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired com.zaxxer.hikari.HikariDataSource pool;
    @Autowired WebApplicationContext context;
    @Autowired RefreshTokenService sessions;
    @Autowired JwtService jwt;
    @MockitoBean TranslationClient translations;
    @MockitoBean AchievementService achievements;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    TransactionTemplate tx;
    MockMvc mvc;
    @BeforeEach void setup() {
        tx=new TransactionTemplate(manager);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(translations.translateAll(anyString(),anyList())).thenAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"Provider must never run inside a DB transaction");
            Map<String,String> values=new LinkedHashMap<>();for(String word:call.<List<String>>getArgument(1))values.put(word,"Meaning: "+word);return values;
        });
    }
    User account(){return tx.execute(s->{
        var course=new Course();course.setLanguageCode(UUID.randomUUID().toString().substring(0,8));course.setLanguageName("Test language");em.persist(course);
        var user=new User();user.setEmail(UUID.randomUUID()+"@example.test");user.setName("Learner");user.setVerified(true);user.setActiveCourse(course);em.persist(user);em.flush();return user;
    });}
    String access(User user){var session=sessions.createSession(user.getUserId());return "Bearer "+jwt.generateSessionToken(
            org.springframework.security.core.userdetails.User.withUsername(user.getEmail()).password("").roles("STUDENT").build(),session.id());}

    Course otherCourse() { return tx.execute(s -> {
        var c=new Course();c.setLanguageCode(UUID.randomUUID().toString().substring(0,8));c.setLanguageName("Other course");em.persist(c);em.flush();return c;
    }); }
    @Test void repeatedIntentIsIdempotentAndOldOrReusedIntentsCannotOverwrite() {
        User u=account();Course b=otherCourse();UUID first=UUID.randomUUID();
        var accepted=profiles.changeActiveCourse(u.getEmail(),b.getLanguageCode(),0,first);
        assertEquals(1,accepted.getActiveCourseRevision());
        assertEquals(1,profiles.changeActiveCourse(u.getEmail(),b.getLanguageCode(),0,first).getActiveCourseRevision());
        assertThrows(ConflictException.class,()->profiles.changeActiveCourse(u.getEmail(),u.getActiveCourse().getLanguageCode(),1,first));
        profiles.changeActiveCourse(u.getEmail(),u.getActiveCourse().getLanguageCode(),1,UUID.randomUUID());
        assertThrows(ConflictException.class,()->profiles.changeActiveCourse(u.getEmail(),b.getLanguageCode(),0,first));
        var current=users.findCourseSelection(u.getEmail()).orElseThrow();
        assertEquals(u.getActiveCourse().getCourseId(),current.courseId());assertEquals(2,current.revision());
    }
    @Test void concurrentDifferentIntentsHaveOnlyOneWinner() throws Exception {
        User u=account();Course b=otherCourse(),c=otherCourse();var workers=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> attempts=new ArrayList<>();
            for(Course target:List.of(b,c))attempts.add(workers.submit(()->{start.await();try {
                profiles.changeActiveCourse(u.getEmail(),target.getLanguageCode(),0,UUID.randomUUID());return true;
            } catch(ConflictException expected){return false;}}));
            start.countDown();int wins=0;for(var attempt:attempts)if(attempt.get(15,TimeUnit.SECONDS))wins++;
            assertEquals(1,wins);assertEquals(1,users.findCourseSelection(u.getEmail()).orElseThrow().revision());
        } finally {workers.shutdownNow();}
    }
    @Test void staleManagedUserSaveCannotRollBackCourseMetadata() throws Exception {
        User u=account();Course b=otherCourse();var worker=Executors.newSingleThreadExecutor();UUID intent=UUID.randomUUID();
        try {tx.executeWithoutResult(s->{
            var stale=users.findById(u.getUserId()).orElseThrow();
            try {worker.submit(()->profiles.changeActiveCourse(u.getEmail(),b.getLanguageCode(),0,intent)).get(10,TimeUnit.SECONDS);}
            catch(Exception e){throw new RuntimeException(e);}
            stale.setXp(777);users.saveAndFlush(stale);
        });}finally{worker.shutdownNow();}
        var current=users.findById(u.getUserId()).orElseThrow();
        assertEquals(777,current.getXp());assertEquals(1,current.getActiveCourseRevision());assertEquals(intent,current.getLastCourseIntentId());
        assertEquals(b.getCourseId(),users.findCourseSelection(u.getEmail()).orElseThrow().courseId());
    }
    @Test void sameCourseIntentAdvancesRevisionAndInvalidTargetDoesNot() {
        User u=account();profiles.changeActiveCourse(u.getEmail(),u.getActiveCourse().getLanguageCode(),0,UUID.randomUUID());
        assertThrows(ResourceNotFoundException.class,()->profiles.changeActiveCourse(u.getEmail(),"missing-course",1,UUID.randomUUID()));
        assertEquals(1,users.findCourseSelection(u.getEmail()).orElseThrow().revision());
    }
    @Test void explicitLookupKeepsItsCourseDuringASlowProviderCall() {
        User u=account();Course b=otherCourse();
        when(translations.translateAll(anyString(),anyList())).thenAnswer(call->{
            assertEquals(u.getActiveCourse().getLanguageCode(),call.getArgument(0));
            profiles.changeActiveCourse(u.getEmail(),b.getLanguageCode(),0,UUID.randomUUID());return Map.of("apple","alma");
        });
        service.lookupAndSaveWord(u.getEmail(),"apple","LESSON",u.getActiveCourse().getCourseId());
        assertEquals(1,words.findAllByUser_UserIdAndCourse_CourseIdOrderByFirstSeenAtDesc(u.getUserId(),u.getActiveCourse().getCourseId()).size());
        assertTrue(words.findAllByUser_UserIdAndCourse_CourseIdOrderByFirstSeenAtDesc(u.getUserId(),b.getCourseId()).isEmpty());
    }
    @Test void knownBatchAndReadsUseExplicitCourseAndPracticeRejectsMismatchedScope() {
        User u=account();Course b=otherCourse();UUID a=u.getActiveCourse().getCourseId();
        profiles.changeActiveCourse(u.getEmail(),b.getLanguageCode(),0,UUID.randomUUID());
        service.addKnownWordsBatch(u.getEmail(),List.of("pear"),a);
        var word=words.findByUser_UserIdAndCourse_CourseIdAndWordIgnoreCase(u.getUserId(),a,"pear").orElseThrow();
        assertThrows(ConflictException.class,()->service.recordPracticeResult(word.getVocabularyId(),u.getEmail(),true,b.getCourseId()));
        service.recordPracticeResult(word.getVocabularyId(),u.getEmail(),true,a);
        assertEquals(1,service.getVocabularyMap(u.getEmail(),a).size());assertTrue(service.getVocabularyMap(u.getEmail(),b.getCourseId()).isEmpty());
        assertEquals(1,service.getDetailedVocabularyForUser(u.getEmail(),a).size());
        assertTrue(service.getDetailedVocabularyForUser(u.getEmail(),b.getCourseId()).isEmpty());
    }
    @Test void publicDictionaryMutationsRequireAnExplicitCourse() throws Exception {
        User u=account();String bearer=access(u);
        mvc.perform(post("/api/vocabulary/lookup").header("Authorization",bearer).contentType("application/json").content("{\"word\":\"apple\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/vocabulary/known-batch").header("Authorization",bearer).contentType("application/json").content("{\"words\":[\"apple\"]}"))
            .andExpect(status().isBadRequest());
    }
    @Test void notificationDueCountIsPerRequestedCourseAndCarriesCurrentSelection() throws Exception {
        User u=account();Course b=otherCourse();UUID a=u.getActiveCourse().getCourseId();
        service.lookupAndSaveWord(u.getEmail(),"apple",null,a);
        service.lookupAndSaveWord(u.getEmail(),"pear",null,a);
        service.lookupAndSaveWord(u.getEmail(),"birne",null,b.getCourseId());
        profiles.changeActiveCourse(u.getEmail(),b.getLanguageCode(),0,UUID.randomUUID());
        mvc.perform(get("/api/notifications/summary").param("courseId",a.toString()).header("Authorization",access(u)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.dueVocabularyCount").value(2))
            .andExpect(jsonPath("$.vocabularyCourseId").value(a.toString()))
            .andExpect(jsonPath("$.activeCourseId").value(b.getCourseId().toString()))
            .andExpect(jsonPath("$.activeCourseRevision").value(1));
    }
    @Test void staleHttpSelectionReturnsConflictAndMissingRevisionReturnsBadRequest() throws Exception {
        User u=account();Course b=otherCourse();String bearer=access(u);
        mvc.perform(patch("/api/users/me/active-course").param("courseCode",b.getLanguageCode()).header("Authorization",bearer))
            .andExpect(status().isBadRequest());
        profiles.changeActiveCourse(u.getEmail(),b.getLanguageCode(),0,UUID.randomUUID());
        mvc.perform(patch("/api/users/me/active-course").param("courseCode",u.getActiveCourse().getLanguageCode()).param("expectedRevision","0")
            .param("intentId",UUID.randomUUID().toString()).header("Authorization",bearer)).andExpect(status().isConflict());
    }

    @Test void lessonResponseAndDashboardReadsKeepTheExplicitCourseAfterGlobalSwitch() {
        User u=account();UUID a=u.getActiveCourse().getCourseId();Course b=otherCourse();
        UUID lessonId=tx.execute(s->{
            LessonTopic topic=new LessonTopic();topic.setCourse(em.getReference(Course.class,a));topic.setName("Test topic");em.persist(topic);
            Lesson lesson=new Lesson();lesson.setTopic(topic);lesson.setTitle("English lesson");lesson.setLanguage("en");em.persist(lesson);
            Exercise exercise=new Exercise();exercise.setLesson(lesson);exercise.setDifficulty("EASY");exercise.setType("TRANSLATION");
            exercise.setContent(Map.of("question","apple","questionLang","en","answerLang","hu"));exercise.setCorrectAnswer(Map.of("answer","alma"));em.persist(exercise);
            return lesson.getLessonId();
        });
        var attempt=lessons.startAttempt(lessonId,u.getEmail(),null,null);
        assertEquals(a,attempt.courseId());assertEquals(u.getActiveCourse().getLanguageCode(),attempt.courseCode());
        profiles.changeActiveCourse(u.getEmail(),b.getLanguageCode(),0,UUID.randomUUID());
        assertEquals(1,lessons.getAllLessonsForUser(u.getEmail(),a).size());assertTrue(lessons.getAllLessonsForUser(u.getEmail(),b.getCourseId()).isEmpty());
        assertEquals(a,attempt.courseId());
    }
}
