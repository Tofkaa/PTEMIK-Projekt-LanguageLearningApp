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

@EnabledIfEnvironmentVariable(named="TRANSLATION_ISOLATION_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_translation_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${TRANSLATION_ISOLATION_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.show-sql=false","spring.datasource.hikari.maximum-pool-size=2","spring.datasource.hikari.connection-timeout=1000",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class TranslationIsolationPersistenceTest {
    @Autowired VocabularyService service;
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
    @Test void lookupAndKnownBatchKeepExistingSrsAndSkipExternalCallsOnHits() {
        User user=account();var first=service.lookupAndSaveWord(user.getEmail()," Apple ","LESSON");assertTrue(first.isNewAddition());
        tx.executeWithoutResult(s->words.findById(first.getVocabularyId()).orElseThrow().setSrsLevel(5));
        var again=service.lookupAndSaveWord(user.getEmail(),"APPLE","OTHER");assertFalse(again.isNewAddition());assertEquals(5,again.getSrsLevel());
        service.addKnownWordsBatch(user.getEmail(),List.of("apple","Pear","pear"));
        assertEquals(2,words.findAllByUser_UserIdAndCourse_CourseIdOrderByFirstSeenAtDesc(user.getUserId(),user.getActiveCourse().getCourseId()).size());
        assertEquals(2,words.findByUser_UserIdAndCourse_CourseIdAndWordIgnoreCase(user.getUserId(),user.getActiveCourse().getCourseId(),"pear").orElseThrow().getSrsLevel());
        verify(translations,times(2)).translateAll(anyString(),anyList());
    }
    @Test void twoSlowHttpRequestsDoNotOccupyTheTwoConnectionDatabasePoolOrOverwriteXp()throws Exception {
        User user=account();String bearer=access(user);var entered=new CountDownLatch(2);var release=new CountDownLatch(1);var workers=Executors.newFixedThreadPool(2);
        when(translations.translateAll(anyString(),anyList())).thenAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));return Map.of(call.<List<String>>getArgument(1).getFirst(),"valid meaning");
        });
        try {
            Callable<Integer> lookup=()->mvc.perform(post("/api/vocabulary/lookup").header("Authorization",bearer).contentType("application/json").content("{\"word\":\"apple\"}"))
                    .andReturn().getResponse().getStatus();
            var first=workers.submit(lookup);var second=workers.submit(lookup);assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertEquals(0,pool.getHikariPoolMXBean().getActiveConnections(),"Slow provider must not retain a JDBC connection with the production Open-in-View setting disabled");
            tx.executeWithoutResult(s->users.findForSessionUpdate(user.getUserId()).orElseThrow().setXp(777));
            doAnswer(call->{assertEquals(777,call.<User>getArgument(0).getXp());return null;}).when(achievements).checkAndAwardAchievements(any(),anyInt());
            release.countDown();assertEquals(200,first.get(10,TimeUnit.SECONDS));assertEquals(200,second.get(10,TimeUnit.SECONDS));
            assertEquals(777,users.findById(user.getUserId()).orElseThrow().getXp());
            assertEquals(1,words.findAllByUser_UserIdAndCourse_CourseIdOrderByFirstSeenAtDesc(user.getUserId(),user.getActiveCourse().getCourseId()).size());
        }finally{release.countDown();workers.shutdownNow();}
    }
    @Test void courseChangeDuringProviderWaitRejectsTheLateWrite() {
        User user=account();when(translations.translateAll(anyString(),anyList())).thenAnswer(call->{
            tx.executeWithoutResult(s->{var other=new Course();other.setLanguageCode(UUID.randomUUID().toString().substring(0,8));other.setLanguageName("Other");em.persist(other);users.findForSessionUpdate(user.getUserId()).orElseThrow().setActiveCourse(other);});
            return Map.of("apple","alma");
        });
        assertThrows(ConflictException.class,()->service.lookupAndSaveWord(user.getEmail(),"apple",null));assertTrue(words.findAllByUser_UserIdAndCourse_CourseIdOrderByFirstSeenAtDesc(user.getUserId(),user.getActiveCourse().getCourseId()).isEmpty());
    }
    @Test void deactivationDuringProviderWaitCannotCreateVocabulary() {
        User user=account();long before=words.count();when(translations.translateAll(anyString(),anyList())).thenAnswer(call->{
            tx.executeWithoutResult(s->users.findForSessionUpdate(user.getUserId()).orElseThrow().setActive(false));return Map.of("apple","alma");});
        assertThrows(ResourceNotFoundException.class,()->service.lookupAndSaveWord(user.getEmail(),"apple",null));assertEquals(before,words.count());
    }
    @Test void unavailableTranslationReturns503AndDoesNotSaveAnyNewWord()throws Exception {
        User user=account();long before=words.count();when(translations.translateAll(anyString(),anyList())).thenThrow(new TranslationUnavailableException());
        mvc.perform(post("/api/vocabulary/lookup").header("Authorization",access(user)).contentType("application/json").content("{\"word\":\"apple\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After","30"));
        assertThrows(TranslationUnavailableException.class,()->service.addKnownWordsBatch(user.getEmail(),List.of("apple","pear")));assertEquals(before,words.count());
    }
    @Test void databaseUniqueConstraintRejectsDuplicateCanonicalWords() {
        User user=account();service.lookupAndSaveWord(user.getEmail(),"apple",null);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.executeWithoutResult(s->{
            var duplicate=new StudentVocabulary();duplicate.setUser(users.findById(user.getUserId()).orElseThrow());duplicate.setCourse(duplicate.getUser().getActiveCourse());duplicate.setWord("apple");duplicate.setTranslation("different");words.saveAndFlush(duplicate);
        }));
    }
    @Test void ambientTransactionIsRejectedBeforeAnyProviderCall() {
        User user=account();assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,()->tx.executeWithoutResult(s->service.lookupAndSaveWord(user.getEmail(),"apple",null)));
        verifyNoInteractions(translations);
    }
    @Test void legacyFallbackIsRepairedWithoutResettingSrsOrCreatingANewWord() {
        User user=account();var original=service.lookupAndSaveWord(user.getEmail(),"apple",null);
        tx.executeWithoutResult(s->{var entry=words.findById(original.getVocabularyId()).orElseThrow();entry.setTranslation("Fordítás nem érhető el");entry.setSrsLevel(5);});
        var repaired=service.lookupAndSaveWord(user.getEmail(),"apple",null);
        assertEquals(original.getVocabularyId(),repaired.getVocabularyId());assertEquals(5,repaired.getSrsLevel());assertFalse(repaired.isNewAddition());assertEquals("Meaning: apple",repaired.getTranslation());
        tx.executeWithoutResult(s->words.findById(original.getVocabularyId()).orElseThrow().setTranslation("Ismeretlen jelentés"));
        service.addKnownWordsBatch(user.getEmail(),List.of("apple"));var preserved=words.findById(original.getVocabularyId()).orElseThrow();
        assertEquals(5,preserved.getSrsLevel());assertEquals("Meaning: apple",preserved.getTranslation());
    }
    @Test void concurrentSrsChangeSurvivesALegacyTranslationRepair()throws Exception {
        User user=account();var original=service.lookupAndSaveWord(user.getEmail(),"apple",null);String bearer=access(user);
        tx.executeWithoutResult(s->{var entry=words.findById(original.getVocabularyId()).orElseThrow();entry.setTranslation("Fordítás nem érhető el");entry.setSrsLevel(5);});
        var concurrent=Executors.newSingleThreadExecutor();
        when(translations.translateAll(anyString(),anyList())).thenAnswer(call->{
            concurrent.submit(()->tx.executeWithoutResult(s->words.findById(original.getVocabularyId()).orElseThrow().setSrsLevel(6))).get(5,TimeUnit.SECONDS);
            return Map.of("apple","alma");
        });
        try {
            mvc.perform(post("/api/vocabulary/lookup").header("Authorization",bearer).contentType("application/json").content("{\"word\":\"apple\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.srsLevel").value(6));
            assertEquals(6,words.findById(original.getVocabularyId()).orElseThrow().getSrsLevel());
        }finally{concurrent.shutdownNow();}
    }
    @Test void outerPersistenceFailureRollsBackAnEntireKnownWordBatch() {
        User user=account();var snapshot=store.snapshot(user.getEmail(),List.of("apple","pear"));
        doThrow(new IllegalStateException("synthetic achievement failure")).when(achievements).checkAndAwardAchievements(any(),anyInt());
        assertThrows(IllegalStateException.class,()->store.saveKnown(user.getEmail(),snapshot,Map.of("apple","alma","pear","korte")));
        assertTrue(words.findAllByUser_UserIdAndCourse_CourseIdOrderByFirstSeenAtDesc(user.getUserId(),user.getActiveCourse().getCourseId()).isEmpty());
    }
}
