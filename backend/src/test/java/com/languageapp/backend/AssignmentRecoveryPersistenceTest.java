package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.dto.request.*;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.MembershipStatus;
import com.languageapp.backend.exception.ConflictException;
import com.languageapp.backend.repository.AssignmentSessionRepository;
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
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Never creates tables in an application database: opt-in, disposable database name is mandatory. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_RECOVERY_TEST_DATABASE_URL", matches="jdbc:postgresql://[^/]+/languageapp_exam_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${ASSIGNMENT_RECOVERY_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create","spring.jpa.show-sql=false"})
class AssignmentRecoveryPersistenceTest {
    @Autowired AssignmentService service;
    @Autowired AssignmentSessionRepository sessions;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean StreakService streak;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean Clock clock;
    final Instant now=Instant.parse("2026-10-24T16:00:00Z");
    TransactionTemplate tx;
    record Fixture(UUID session, String email, List<UUID> queue) {}
    @BeforeEach void setup() { tx=new TransactionTemplate(manager); when(clock.instant()).thenReturn(now); }

    Fixture fixture() {
        record Created(UUID id,String email) {}
        Created created=tx.execute(status->{
            String key=UUID.randomUUID().toString().substring(0,8);
            User teacher=new User(); teacher.setName("Teacher"); teacher.setEmail("t"+key+"@example.test"); em.persist(teacher);
            User student=new User(); student.setName("Student"); student.setEmail("s"+key+"@example.test"); em.persist(student);
            Course course=new Course(); course.setLanguageCode(key); course.setLanguageName("Test"); em.persist(course);
            LessonTopic topic=new LessonTopic(); topic.setCourse(course); topic.setName("Test"); em.persist(topic);
            Lesson lesson=new Lesson(); lesson.setTopic(topic); lesson.setTitle("Test"); em.persist(lesson);
            List<Exercise> exercises=new ArrayList<>();
            for(int i=0;i<3;i++){ Exercise e=new Exercise(); e.setLesson(lesson); e.setType("TRANSLATION");e.setDifficulty("EASY");
                e.setContent(Map.of("question","Alma"));e.setCorrectAnswer(Map.of("answer","apple"));em.persist(e);exercises.add(e); }
            Classroom room=new Classroom();room.setName("Test");room.setTeacher(teacher);room.setCourse(course);room.setInviteCode(key);em.persist(room);
            ClassroomMember member=new ClassroomMember();member.setClassroom(room);member.setUser(student);member.setStatus(MembershipStatus.ACCEPTED);em.persist(member);
            ClassroomAssignment a=new ClassroomAssignment();a.setClassroom(room);a.setTitle("Recovery");a.setExercises(exercises);
            a.setGenerationMode("RANDOM_SUBSET");a.setQuestionCount(2);a.setTimeLimitMinutes(1);a.setAllowLateSubmission(false);em.persist(a);em.flush();
            return new Created(a.getAssignmentId(),student.getEmail());
        });
        var started=service.startAssignment(created.id(),created.email());
        return new Fixture(started.getSessionId(),created.email(),started.getExercises().stream().map(Exercise::getExerciseId).toList());
    }
    AssignmentDraftRequest draft(Fixture f) {var d=new AssignmentDraftRequest();d.setQueue(f.queue());d.setCurrentAnswer("apple");return d;}
    AssignmentSubmitRequest submit(Fixture f) {var r=new AssignmentSubmitRequest();r.setRevision(0L);var a=new ExerciseSubmission();a.setExerciseId(f.queue().getFirst());a.setAnswer("apple");r.setAnswers(List.of(a));return r;}

    @Test void allocationAndPartialInputSurviveIndependentTransactions() {
        Fixture f=fixture();service.saveDraft(f.session(),draft(f),f.email());
        var recovered=service.recoverSession(f.session(),f.email());
        assertEquals(f.queue(),recovered.sessionData().getExercises().stream().map(Exercise::getExerciseId).toList());
        assertEquals("apple",recovered.draft().getCurrentAnswer());assertEquals(1,recovered.revision());
    }
    @Test void simultaneousDraftsAtSameRevisionHaveExactlyOneWinner() throws Exception {
        Fixture f=fixture();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        Callable<Boolean> save=()->{start.await();try{service.saveDraft(f.session(),draft(f),f.email());return true;}catch(ConflictException expected){return false;}};
        try {var a=pool.submit(save);var b=pool.submit(save);start.countDown();assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));}
        finally {pool.shutdownNow();}
        assertEquals(1,service.recoverSession(f.session(),f.email()).revision());
    }
    @Test void parallelFinalSubmissionsAreIdempotentAcrossTransactions() throws Exception {
        Fixture f=fixture();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        Callable<Boolean> finish=()->{start.await();service.submitAssignment(f.session(),submit(f),f.email());return true;};
        try {var a=pool.submit(finish);var b=pool.submit(finish);start.countDown();assertTrue(a.get(20,TimeUnit.SECONDS));assertTrue(b.get(20,TimeUnit.SECONDS));}
        finally {pool.shutdownNow();}
        assertNotNull(sessions.findById(f.session()).orElseThrow().getFinishedAt());
        verify(streak,times(1)).updateActivity(any());verify(sse,times(1)).sendPing(any());
    }
    @Test void expiredAttemptUsesPersistedDraftAndLogicalDeadlineWithoutBrowser() {
        Fixture f=fixture();service.saveDraft(f.session(),draft(f),f.email());
        when(clock.instant()).thenReturn(now.plusSeconds(70));service.closeExpiredSession(f.session());
        var saved=sessions.findById(f.session()).orElseThrow();assertEquals(now.plusSeconds(60),saved.getFinishedAt());
        assertTrue(saved.getRawAnswers().contains("apple"));assertEquals(50,saved.getFinalScore());
    }

    @Test void lateEnabledOngoingAttemptRemainsDiscoverableAfterCommonDeadline() {
        Fixture f=fixture();
        tx.execute(status->{var a=em.find(AssignmentSession.class,f.session()).getAssignment();
            a.setAvailableUntil(now.plusSeconds(30));a.setAllowLateSubmission(true);return null;});
        when(clock.instant()).thenReturn(now.plusSeconds(120));
        var active=service.getActiveAssignmentsForStudent(f.email());
        assertEquals(1,active.size());assertEquals(f.session(),active.getFirst().getOngoingSessionId());
        service.submitAssignment(f.session(),submit(f),f.email());
        assertTrue(service.getActiveAssignmentsForStudent(f.email()).isEmpty());
    }
}
