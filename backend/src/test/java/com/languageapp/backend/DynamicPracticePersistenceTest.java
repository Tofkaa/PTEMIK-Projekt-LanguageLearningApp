package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.dto.request.*;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.exception.*;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.security.*;
import com.languageapp.backend.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="DYNAMIC_PRACTICE_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_practice_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${DYNAMIC_PRACTICE_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create","spring.jpa.show-sql=false",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class DynamicPracticePersistenceTest {
    static final Instant NOW=Instant.parse("2026-10-28T10:00:00Z");
    @Autowired DynamicPracticeService service;
    @Autowired VocabularyService vocabulary;
    @Autowired DynamicPracticeAttemptRepository attempts;
    @Autowired StudentVocabularyRepository words;
    @Autowired UserRepository users;
    @Autowired UserService profiles;
    @Autowired RefreshTokenService sessions;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired WebApplicationContext context;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @MockitoBean Clock clock;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean StreakService streak;
    @MockitoBean AchievementService achievements;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    TransactionTemplate tx;
    record Fixture(UUID user,String email,UUID course,List<UUID> words) { }
    @BeforeEach void setup(){tx=new TransactionTemplate(manager);when(clock.instant()).thenReturn(NOW);when(clock.getZone()).thenReturn(ZoneOffset.UTC);}
    Fixture fixture(int count,boolean due){return tx.execute(s->{
        String key=UUID.randomUUID().toString().substring(0,8);
        var course=new Course();course.setLanguageCode(key);course.setLanguageName("Practice test");em.persist(course);
        var user=new User();user.setName("Practice "+key);user.setEmail(key+"@example.test");user.setActiveCourse(course);user.setXp(10);user.setVerified(true);user.setPasswordHash(encoder.encode("Practice-password"));em.persist(user);
        var ids=new ArrayList<UUID>();for(int i=0;i<count;i++){var word=new StudentVocabulary();word.setUser(user);word.setCourse(course);word.setWord("green apple "+i);word.setTranslation("zöld alma "+i);word.setSrsLevel(1);word.setNextPracticeAt(due?NOW.minusSeconds(10):NOW.plusSeconds(3600));em.persist(word);em.flush();ids.add(word.getVocabularyId());}
        return new Fixture(user.getUserId(),user.getEmail(),course.getCourseId(),ids);
    });}
    DynamicPracticeSubmitRequest submit(UUID id,boolean correct){var a=attempts.findById(id).orElseThrow();var request=new DynamicPracticeSubmitRequest();request.setAttemptId(id);
        request.setResults(a.getQuestions().stream().map(q->{var answer=new DynamicPracticeSubmitRequest.AnswerResult();answer.setExerciseId(q.exercise().getExerciseId());answer.setAnswer(correct?q.expectedAnswer():"WRONG");return answer;}).toList());return request;}
    int xp(Fixture f){return users.findById(f.user).orElseThrow().getXp();}

    @Test void publicAllocationContainsLanguagesAndNoPrematureSolutionsOrTargetWord(){
        Fixture f=fixture(10,true);var started=service.start(f.email,f.course);assertEquals(10,started.exercises().size());
        String payload=json.writeValueAsString(started);assertFalse(payload.contains("\"correctAnswer\""));assertFalse(payload.contains("\"targetWord\""));assertFalse(payload.contains("\"hint\""));
        for(var e:started.exercises()){assertNotNull(e.getAnswerLang());assertEquals("hu",e.getQuestionLang());if(e.getQuestionParts()!=null)assertEquals(e.getQuestion(),e.getQuestionParts().stream().map(p->p.text()).collect(java.util.stream.Collectors.joining()));}
        assertEquals(started.attemptId(),service.start(f.email,f.course).attemptId());
    }
    @Test void startIsBoundToActiveCourseAndOnlyAllocatesDueOwnedWords(){
        Fixture f=fixture(15,true),other=fixture(2,true);var start=service.start(f.email,f.course);assertEquals(10,start.exercises().size());
        assertThrows(ConflictException.class,()->service.start(f.email,other.course));
        assertTrue(attempts.findById(start.attemptId()).orElseThrow().getQuestions().stream().allMatch(q->f.words.contains(q.vocabularyId())));
    }
    @Test void noDueWordsCreateNoAttemptAndNoReward(){Fixture f=fixture(1,false);var start=service.start(f.email,f.course);assertNull(start.attemptId());assertTrue(start.exercises().isEmpty());assertTrue(attempts.findByUserUserId(f.user).isEmpty());assertEquals(10,xp(f));verifyNoInteractions(streak,achievements);}
    @Test void rawCorrectAnswersAreEvaluatedAndRewardedExactlyOnceAcrossRetries(){
        Fixture f=fixture(1,true);var start=service.start(f.email,f.course);var request=submit(start.attemptId(),true);
        var first=service.submit(f.email,request);var repeat=service.submit(f.email,request);assertEquals(first,repeat);assertEquals(12,first.get("earnedXp"));assertEquals(22,xp(f));
        var word=words.findById(f.words.getFirst()).orElseThrow();assertEquals(2,word.getSrsLevel());assertEquals(NOW.plus(Duration.ofDays(3)),word.getNextPracticeAt());
        verify(streak,times(1)).updateActivity(any());verify(achievements,times(1)).checkAndAwardAchievements(any(),eq(100));assertTrue(service.start(f.email,f.course).exercises().isEmpty());
    }
    @Test void firstCheckedWrongAnswerCannotBeChangedAfterRevealingTheSolution(){
        Fixture f=fixture(1,true);var start=service.start(f.email,f.course);var good=submit(start.attemptId(),true);var q=good.getResults().getFirst();
        var bad=new DynamicPracticeCheckRequest(start.attemptId(),q.getExerciseId(),"WRONG");var first=service.check(f.email,bad);assertFalse(first.correct());
        var retry=service.check(f.email,new DynamicPracticeCheckRequest(start.attemptId(),q.getExerciseId(),q.getAnswer()));assertEquals(first,retry);
        assertThrows(BadRequestException.class,()->service.submit(f.email,good));assertEquals(10,xp(f));assertNull(attempts.findById(start.attemptId()).orElseThrow().getCompletedAt());
        assertEquals(0,service.submit(f.email,submit(start.attemptId(),false)).get("earnedXp"));assertEquals(0,words.findById(f.words.getFirst()).orElseThrow().getSrsLevel());
    }
    @Test void checkingReturnsTheServerAcceptedAnswerWithoutAwardingXp(){Fixture f=fixture(1,true);var start=service.start(f.email,f.course);var q=submit(start.attemptId(),true).getResults().getFirst();
        var checked=service.check(f.email,new DynamicPracticeCheckRequest(start.attemptId(),q.getExerciseId(),"  "+q.getAnswer().toUpperCase(Locale.ROOT)+"  "));assertTrue(checked.correct());assertEquals(10,xp(f));assertNull(words.findById(f.words.getFirst()).orElseThrow().getLastPracticedAt());verifyNoInteractions(streak,achievements);}
    @Test void forgedForeignQuestionAndDuplicateIdsRollBackWithoutAnySrsOrXp(){Fixture f=fixture(2,true);var start=service.start(f.email,f.course);var request=submit(start.attemptId(),true);
        request.getResults().getLast().setExerciseId(UUID.randomUUID());assertThrows(BadRequestException.class,()->service.submit(f.email,request));assertEquals(10,xp(f));
        for(UUID id:f.words)assertEquals(1,words.findById(id).orElseThrow().getSrsLevel());
        var duplicate=submit(start.attemptId(),true);duplicate.getResults().getLast().setExerciseId(duplicate.getResults().getFirst().getExerciseId());assertThrows(BadRequestException.class,()->service.submit(f.email,duplicate));verifyNoInteractions(streak,achievements);
    }
    @Test void foreignUserAndCourseSwitchCannotUseTheAttempt(){Fixture f=fixture(1,true),other=fixture(1,true);var start=service.start(f.email,f.course);var request=submit(start.attemptId(),true);
        assertThrows(ForbiddenException.class,()->service.submit(other.email,request));tx.executeWithoutResult(s->users.findForSessionUpdate(f.user).orElseThrow().setActiveCourse(em.getReference(Course.class,other.course)));
        assertThrows(ConflictException.class,()->service.submit(f.email,request));assertEquals(10,xp(f));assertNull(attempts.findById(start.attemptId()).orElseThrow().getCompletedAt());
    }
    @Test void aWordPracticedElsewhereCannotEarnDuplicateXpOrThePerfectBonus(){Fixture f=fixture(1,true);var start=service.start(f.email,f.course);
        vocabulary.recordPracticeResult(f.words.getFirst(),f.email,true);var before=words.findById(f.words.getFirst()).orElseThrow().getNextPracticeAt();clearInvocations(achievements);
        assertEquals(0,service.submit(f.email,submit(start.attemptId(),true)).get("earnedXp"));assertEquals(10,xp(f));assertEquals(before,words.findById(f.words.getFirst()).orElseThrow().getNextPracticeAt());verifyNoInteractions(streak);verify(achievements,never()).checkAndAwardAchievements(any(),anyInt());}
    @Test void concurrentStartsShareOnePendingAllocation()throws Exception{Fixture f=fixture(1,true);var pool=Executors.newFixedThreadPool(2);var go=new CountDownLatch(1);
        try{Callable<UUID> job=()->{go.await();return service.start(f.email,f.course).attemptId();};var a=pool.submit(job);var b=pool.submit(job);go.countDown();assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));assertEquals(1,attempts.findByUserUserId(f.user).size());}finally{go.countDown();pool.shutdownNow();}}
    @Test void concurrentSubmissionsHaveOneRewardAndOneSrsTransition()throws Exception{Fixture f=fixture(1,true);var start=service.start(f.email,f.course);var request=submit(start.attemptId(),true);var pool=Executors.newFixedThreadPool(2);var go=new CountDownLatch(1);
        try{Callable<Map<String,Object>> job=()->{go.await();return service.submit(f.email,request);};var a=pool.submit(job);var b=pool.submit(job);go.countDown();assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));}finally{go.countDown();pool.shutdownNow();}
        assertEquals(22,xp(f));assertEquals(2,words.findById(f.words.getFirst()).orElseThrow().getSrsLevel());verify(streak,times(1)).updateActivity(any());}
    @Test void rollbackDoesNotLeaveARewardOrConsumedAttempt(){Fixture f=fixture(1,true);var start=service.start(f.email,f.course);tx.executeWithoutResult(s->{service.submit(f.email,submit(start.attemptId(),true));s.setRollbackOnly();});assertEquals(10,xp(f));assertNull(attempts.findById(start.attemptId()).orElseThrow().getResult());assertEquals(12,service.submit(f.email,submit(start.attemptId(),true)).get("earnedXp"));}
    @Test void accountExportIncludesRawPracticeDataAndDeletionRemovesIt(){Fixture f=fixture(1,true);var start=service.start(f.email,f.course);var q=submit(start.attemptId(),true).getResults().getFirst();service.check(f.email,new DynamicPracticeCheckRequest(start.attemptId(),q.getExerciseId(),"WRONG"));
        var data=profiles.exportUserData(f.email);assertEquals(1,((List<?>)data.get("dynamicPracticeAttempts")).size());assertFalse(json.writeValueAsString(data.get("dynamicPracticeAttempts")).contains("expectedAnswer"));
        profiles.deleteAndAnonymizeMyAccount(f.email,"Practice-password",true);assertTrue(attempts.findByUserUserId(f.user).isEmpty());assertFalse(attempts.existsById(start.attemptId()));}
    @Test void postedCorrectFlagDoesNotOverrideTheWrongRawAnswer()throws Exception{Fixture f=fixture(1,true);var start=service.start(f.email,f.course);var sid=sessions.createSession(f.user);String token=jwt.generateSessionToken(org.springframework.security.core.userdetails.User.withUsername(f.email).password("unused").roles("STUDENT").build(),sid.id());
        String body="{\"attemptId\":\""+start.attemptId()+"\",\"results\":[{\"exerciseId\":\""+start.exercises().getFirst().getExerciseId()+"\",\"answer\":\"WRONG\",\"isCorrect\":true}]}";
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build().perform(post("/api/vocabulary/practice/submit").header("Authorization","Bearer "+token).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.earnedXp").value(0));assertEquals(10,xp(f));}
    @Test void aPersistedAchievementResponseRetainsItsWireShapeOnRetry() {
        Fixture f=fixture(1,true);var started=service.start(f.email,f.course);
        var award=new com.languageapp.backend.dto.response.AchievementFeedback(UUID.randomUUID(),"Award","Description",null,"2026-10-28T10:00:00Z");
        when(achievements.checkAndAwardAchievements(any(),anyInt())).thenReturn(List.of(award));
        var first=service.submit(f.email,submit(started.attemptId(),true));
        var retry=service.submit(f.email,submit(started.attemptId(),true));
        assertEquals(json.readTree(json.writeValueAsString(first)),json.readTree(json.writeValueAsString(retry)));
        assertEquals(22,xp(f));verify(achievements,times(1)).checkAndAwardAchievements(any(),anyInt());
    }
    @Test void aDeletedDictionaryEntryCannotBeRewardedFromAnOlderAllocation() {
        Fixture f=fixture(1,true);var start=service.start(f.email,f.course);var request=submit(start.attemptId(),true);
        tx.executeWithoutResult(s->words.deleteById(f.words.getFirst()));
        assertEquals(0,service.submit(f.email,request).get("earnedXp"));assertEquals(10,xp(f));verifyNoInteractions(streak,achievements);
    }
    @Test void anotherTabCanReadFrozenFeedbackAfterCompletionWithoutAnotherReward() {
        Fixture f=fixture(1,true);var start=service.start(f.email,f.course);var request=submit(start.attemptId(),true);
        var summary=service.submit(f.email,request);var answer=request.getResults().getFirst();
        var frozen=service.check(f.email,new DynamicPracticeCheckRequest(start.attemptId(),answer.getExerciseId(),"WRONG"));
        assertEquals(answer.getAnswer(),frozen.answer());assertTrue(frozen.correct());
        assertEquals(json.readTree(json.writeValueAsString(summary)),json.readTree(json.writeValueAsString(service.submit(f.email,request))));
        assertEquals(22,xp(f));verify(streak,times(1)).updateActivity(any());
    }
}
