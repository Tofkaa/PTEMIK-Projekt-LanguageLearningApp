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
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="EXPIRY_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_expiry_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${EXPIRY_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create","spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.generate_statistics=true","logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class AssignmentExpirySelectionPersistenceTest {
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager manager;
    @Autowired AssignmentSessionRepository sessions;
    @Autowired AssignmentService assignments;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean StreakService streak;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    @MockitoBean Clock clock;
    final Instant now=Instant.parse("2026-10-27T12:00:00Z");
    TransactionTemplate tx;
    record Fixture(User teacher,User student,Course course,Classroom room,Exercise exercise) { }
    @BeforeEach void setup() {
        tx=new TransactionTemplate(manager);when(clock.instant()).thenReturn(now);reset(streak,sse);
        tx.executeWithoutResult(status -> em.createQuery("delete from AssignmentSession").executeUpdate());
    }
    Fixture fixture() {
        return tx.execute(status -> {
            String key=UUID.randomUUID().toString().substring(0,8);Course c=new Course();c.setLanguageCode(key);c.setLanguageName("Expiry");em.persist(c);
            User t=new User();t.setName("Teacher");t.setEmail("t"+key+"@example.test");t.setRole(Role.TEACHER);t.setActiveCourse(c);t.setVerified(true);em.persist(t);
            User u=new User();u.setName("Student");u.setEmail("s"+key+"@example.test");u.setActiveCourse(c);u.setVerified(true);em.persist(u);
            Classroom room=new Classroom();room.setName("Expiry");room.setTeacher(t);room.setCourse(c);room.setInviteCode(key);em.persist(room);
            ClassroomMember m=new ClassroomMember();m.setClassroom(room);m.setUser(u);m.setStatus(MembershipStatus.ACCEPTED);em.persist(m);
            LessonTopic topic=new LessonTopic();topic.setCourse(c);topic.setName("Expiry");em.persist(topic);
            Lesson lesson=new Lesson();lesson.setTopic(topic);lesson.setTitle("Expiry");em.persist(lesson);
            Exercise e=new Exercise();e.setLesson(lesson);e.setDifficulty("EASY");e.setType("TRANSLATION");e.setContent(Map.of("question","Alma"));e.setCorrectAnswer(Map.of("answer","apple"));em.persist(e);
            return new Fixture(t,u,c,room,e);
        });
    }
    AssignmentSession candidate(Fixture f,Consumer<AssignmentSession> edit) {
        ClassroomAssignment a=new ClassroomAssignment();a.setClassroom(em.getReference(Classroom.class,f.room().getClassroomId()));a.setTitle("Expiry");a.setTimeLimitMinutes(1);a.setAllowLateSubmission(false);em.persist(a);
        AssignmentSession s=new AssignmentSession();s.setAssignment(a);s.setUser(em.getReference(User.class,f.student().getUserId()));
        s.setStartedAt(now.minusSeconds(120));s.setTimingInitialized(true);s.setEffectiveTimeLimitMinutes(10);s.setAllocatedExerciseIds("[]");edit.accept(s);em.persist(s);return s;
    }
    @Test void selectionMatchesEffectiveTimingIncludingLegacyUnlimitedAndBoundaryCases() {
        Fixture f=fixture();List<UUID> expected=new ArrayList<>();List<UUID> all=new ArrayList<>();
        tx.executeWithoutResult(status -> {
            List<Consumer<AssignmentSession>> cases=List.of(
                s -> s.setEffectiveTimeLimitMinutes(2),
                s -> s.setEffectiveAvailableUntil(now),
                s -> s.setEffectiveAvailableUntil(now.plusMillis(1)),
                s -> s.setEffectiveTimeLimitMinutes(null),
                s -> {s.setEffectiveTimeLimitMinutes(null);s.setEffectiveAvailableUntil(now.minusSeconds(1));},
                s -> {s.setTimingInitialized(false);s.setEffectiveTimeLimitMinutes(100);},
                s -> {s.setTimingInitialized(false);s.getAssignment().setTimeLimitMinutes(0);},
                s -> {s.setTimingInitialized(false);s.getAssignment().setTimeLimitMinutes(0);s.getAssignment().setAvailableUntil(now);},
                s -> {s.setEffectiveTimeLimitMinutes(0);},
                s -> {s.setEffectiveTimeLimitMinutes(1);s.getAssignment().setAllowLateSubmission(true);},
                s -> {s.setEffectiveTimeLimitMinutes(1);s.setFinishedAt(now.minusSeconds(1));},
                s -> {s.setEffectiveTimeLimitMinutes(1);s.setAllocatedExerciseIds(null);},
                s -> {s.setEffectiveTimeLimitMinutes(1);s.getAssignment().setActive(false);},
                s -> {s.setEffectiveTimeLimitMinutes(null);s.getAssignment().setAvailableUntil(now.minusSeconds(1));},
                s -> {Classroom inactive=new Classroom();inactive.setName("Inactive");inactive.setTeacher(em.getReference(User.class,f.teacher().getUserId()));
                    inactive.setCourse(em.getReference(Course.class,f.course().getCourseId()));inactive.setInviteCode(UUID.randomUUID().toString().substring(0,8));inactive.setActive(false);em.persist(inactive);
                    s.getAssignment().setClassroom(inactive);s.setEffectiveTimeLimitMinutes(1);}
            );
            Set<Integer> due=Set.of(0,1,4,5,7,8);
            for(int i=0;i<cases.size();i++){var s=candidate(f,cases.get(i));all.add(s.getSessionId());if(due.contains(i))expected.add(s.getSessionId());}
        });
        var selected=sessions.findExpiredCandidateIds(now,null,100);assertEquals(new HashSet<>(expected),new HashSet<>(selected));
        for(int i=0;i<all.size();i++) {
            if(i==12||i==14)continue;
            var timing=assignments.getSessionTiming(all.get(i),f.student().getEmail());
            boolean eligible=i!=11 && !timing.finished() && !timing.allowLateSubmission() && timing.deadline()!=null && !now.isBefore(timing.deadline());
            assertEquals(eligible,selected.contains(all.get(i)),"Timing mismatch at case "+i);
        }
    }
    @Test void keysetPagesNeitherRepeatNorSkipDueIds() {
        Fixture f=fixture();Set<UUID> expected=tx.execute(status -> {Set<UUID> ids=new HashSet<>();for(int i=0;i<251;i++)ids.add(candidate(f,s -> s.setEffectiveTimeLimitMinutes(1)).getSessionId());return ids;});
        Set<UUID> seen=new HashSet<>();UUID after=null;int pages=0;
        while(true){var batch=sessions.findExpiredCandidateIds(now,after,100);if(batch.isEmpty())break;assertTrue(batch.size()<=100);for(UUID id:batch)assertTrue(seen.add(id));after=batch.getLast();pages++;}
        assertEquals(expected,seen);assertEquals(3,pages);
    }
    @Test void thousandFutureAttemptsCauseOneScalarQueryAndNoFinalizerCalls() {
        Fixture f=fixture();tx.executeWithoutResult(status -> {for(int i=0;i<1000;i++)candidate(f,s -> {});});
        var statistics=factory.unwrap(SessionFactory.class).getStatistics();statistics.clear();var consumer=mock(AssignmentService.class);
        new AssignmentExpiryScheduler(sessions,consumer,clock).closeExpired();verifyNoInteractions(consumer);
        assertEquals(1,statistics.getPrepareStatementCount());assertEquals(0,statistics.getEntityLoadCount());
    }
    UUID startedAndDue(Fixture f) {
        UUID assignment=tx.execute(status -> {
            ClassroomAssignment a=new ClassroomAssignment();a.setClassroom(em.getReference(Classroom.class,f.room().getClassroomId()));a.setTitle("Real expiry");a.setTimeLimitMinutes(1);a.setAllowLateSubmission(false);
            a.setExercises(List.of(em.getReference(Exercise.class,f.exercise().getExerciseId())));em.persist(a);return a.getAssignmentId();
        });
        UUID id=assignments.startAssignment(assignment,f.student().getEmail()).getSessionId();
        tx.executeWithoutResult(status -> em.find(AssignmentSession.class,id).setStartedAt(now.minusSeconds(120)));return id;
    }
    @Test void selectedCandidateIsRecheckedAfterTeacherExtendsTheAttempt() {
        Fixture f=fixture();UUID id=startedAndDue(f);assertEquals(List.of(id),sessions.findExpiredCandidateIds(now,null,100));
        tx.executeWithoutResult(status -> em.find(AssignmentSession.class,id).setEffectiveTimeLimitMinutes(10));
        assignments.closeExpiredSession(id);assertNull(sessions.findById(id).orElseThrow().getFinishedAt());
        assertTrue(sessions.findExpiredCandidateIds(now,null,100).isEmpty());verifyNoInteractions(sse);
    }
    @Test void independentSchedulersStillFinalizeTheSameAttemptOnlyOnce() throws Exception {
        Fixture f=fixture();UUID id=startedAndDue(f);reset(sse,streak);
        var selected=new CountDownLatch(2);var gatedConsumer=mock(AssignmentService.class);
        doAnswer(call -> {selected.countDown();assertTrue(selected.await(10,TimeUnit.SECONDS));assignments.closeExpiredSession(call.getArgument(0));return null;})
                .when(gatedConsumer).closeExpiredSession(id);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        Callable<Boolean> close=() -> {assertTrue(start.await(5,TimeUnit.SECONDS));new AssignmentExpiryScheduler(sessions,gatedConsumer,clock).closeExpired();return true;};
        try {var one=pool.submit(close);var two=pool.submit(close);start.countDown();assertTrue(one.get(20,TimeUnit.SECONDS));assertTrue(two.get(20,TimeUnit.SECONDS));}
        finally {pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
        var saved=sessions.findById(id).orElseThrow();assertEquals(now.minusSeconds(60),saved.getFinishedAt());assertEquals(0,saved.getFinalScore());
        verify(gatedConsumer,times(2)).closeExpiredSession(id);
        verify(streak).updateActivity(any());verify(sse).sendPing(f.teacher().getEmail());
    }
}
